package kiko

import java.util.concurrent.ConcurrentHashMap


class DataAccess {

    private val slots = ConcurrentHashMap<SlotKey,Slot>()

    fun create(slot: Slot): Boolean {
        return slots.putIfAbsent(SlotKey(slot.flatId, slot.startTime), slot) == null
    }

    fun updateAndGetSlot(flatId: Int, startTime: Long, update: (Slot) -> Unit): Slot? {
        val slot = slots[SlotKey(flatId, startTime)] ?: return null
        synchronized(slot) {
            if (slot.status == SlotStatus.REMOVED) return null
            update(slot)
        }
        return slot
    }

    fun checkAndDelete(flatId: Int, startTime: Long, check: (Slot) -> Boolean): Boolean {
        val slotKey = SlotKey(flatId, startTime)
        val slot = slots.get(slotKey) ?: return false
        synchronized(slot) {
            if (slot.status == SlotStatus.REMOVED)  return false
            if (!check(slot))  return false
            slot.status = SlotStatus.REMOVED
            slots.remove(slotKey)
        }
        return true
    }

    private class SlotKey(
        val flatId: Int,
        val startTime: Long
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as SlotKey

            if (flatId != other.flatId) return false
            if (startTime != other.startTime) return false

            return true
        }

        override fun hashCode(): Int {
            var result = flatId
            result = 31 * result + startTime.hashCode()
            return result
        }
    }
}