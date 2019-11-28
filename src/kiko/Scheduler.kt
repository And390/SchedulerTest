package kiko

import java.time.*
import java.time.format.DateTimeFormatter


class Slot(
    val flatId: Int,
    val visitorId: Int,
    val startTime: Long,
    var status: SlotStatus = SlotStatus.REQUESTED
)

enum class SlotStatus {
    REQUESTED, APPROVED, REJECTED, REMOVED
}

class Scheduler(
    private val timeService: TimeService,
    private val dataAccess: DataAccess,
    private val notificationService: NotificationService
)
{
    companion object {
        val zoneId = ZoneId.systemDefault()

        val slotDuration = 20 * 60    //20 min
        val firstSlotTime = 10 * 60 * 60  //10:00
        val lastSlotTime = 20 * 60 * 60 - slotDuration
        private val slotDurationString = "${slotDuration / 60} minutes"
        private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")
        private val firstSlotTimeString = timeFormat.format(LocalTime.ofSecondOfDay(firstSlotTime.toLong()))
        private val lastSlotTimeString = timeFormat.format(LocalTime.ofSecondOfDay(lastSlotTime.toLong()))

        private val minNotificationTime = 24 * 60 * 60
        private val minNotificationTimeString = "${minNotificationTime / 60 / 60} hours"
    }

    fun reserve(flatId: Int, visitorId: Int, time: Long) {
        if (time % slotDuration != 0L)  throw ClientException("Start time should be multiple of $slotDurationString")
        val zonedTime = Instant.ofEpochSecond(time).atZone(zoneId)
        val date = zonedTime.toLocalDate()
        val sinceStartOfDay = Duration.between(date.atStartOfDay(zoneId), zonedTime).seconds
        if (sinceStartOfDay < firstSlotTime || sinceStartOfDay > lastSlotTime)  throw ClientException("Start time should be between $firstSlotTimeString and $lastSlotTimeString")
        val now = timeService.now() / 1000
        if (time - now < minNotificationTime)  throw ClientException("Start time should be no sooner than $minNotificationTimeString from now")
        if (!isSameWeek(Instant.ofEpochSecond(now).atZone(zoneId).toLocalDate().plusDays(7), date))  throw ClientException("Start time should be in the next week")

        if (!dataAccess.create(Slot(flatId, visitorId, time)))  throw ClientException("Time slot is already reserved")
        notificationService.notifyVisitRequest(flatId, visitorId, time)
    }

    fun approve(flatId: Int, time: Long) {
        val slot = dataAccess.updateAndGetSlot(flatId, time) { slot ->
            if (slot.status != SlotStatus.REQUESTED) throw ClientException("Can not approve reservation because it has already been approved, rejected or canceled")
            slot.status = SlotStatus.APPROVED
        } ?: throw throw ClientException("Reservation not found")
        notificationService.notifyVisitApproved(flatId, slot.visitorId, time)
    }

    fun reject(flatId: Int, time: Long) {
        val slot = dataAccess.updateAndGetSlot(flatId, time) { slot ->
            if (slot.status != SlotStatus.REQUESTED) throw ClientException("Can not reject reservation because it has already been approved, rejected or canceled")
            slot.status = SlotStatus.REJECTED
        } ?: throw throw ClientException("Reservation not found")
        notificationService.notifyVisitRejected(flatId, slot.visitorId, time)
    }

    fun cancel(flatId: Int, visitorId: Int, time: Long) {
        val res = dataAccess.checkAndDelete(flatId, time) { slot ->
            slot.visitorId == visitorId && (slot.status == SlotStatus.REQUESTED || slot.status == SlotStatus.APPROVED)
        }
        if (!res) throw ClientException("Reservation not found")
        notificationService.notifyVisitCanceled(flatId, visitorId, time)
    }


    private fun isSameWeek(date1: LocalDate, date2: LocalDate): Boolean {
        val (a, b) = if (date1 > date2) date1 to date2 else date2 to date1
        val dayDiff = a.toEpochDay() - b.toEpochDay()
        if (dayDiff >= 7)  return false
        return a.dayOfWeek.value >= b.dayOfWeek.value
    }
}
