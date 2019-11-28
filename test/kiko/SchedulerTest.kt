package kiko

import org.testng.Assert.assertThrows
import org.testng.annotations.Test
import java.time.LocalDate


class SchedulerTest {

    private val baseDate = LocalDate.of(2019, 12, 2)  //monday

    private fun LocalDate.atSecondsOfDay(seconds: Int) = this.atTime(seconds/3600, (seconds%3600)/60, (seconds%3600)%60)

    private fun time(dayIndex: Int, secondsFromStartOfDay: Int) =
            baseDate.plusDays(dayIndex.toLong()).atSecondsOfDay(secondsFromStartOfDay).atZone(Scheduler.zoneId).toEpochSecond()

    private fun createScheduler(dayIndex: Int, secondsFromStartOfDay: Int) =
            Scheduler(FakeTimeService(time(dayIndex, secondsFromStartOfDay)*1000), DataAccess(), NotificationService())

    @Test
    fun reserveSlot() {
        val scheduler = createScheduler(6, 0)   //sunday
        scheduler.reserve(1, 1, time(7, Scheduler.firstSlotTime))
        scheduler.reserve(1, 1, time(7, Scheduler.firstSlotTime + Scheduler.slotDuration))
        scheduler.reserve(1, 1, time(7, Scheduler.lastSlotTime))
    }

    @Test(expectedExceptions = [ClientException::class])
    fun timeShouldBeMultipleOfSlotDuration() {
        val scheduler = createScheduler(6, 0)
        scheduler.reserve(1, 1, time(7, Scheduler.firstSlotTime + 1))
    }

    @Test(expectedExceptions = [ClientException::class])
    fun cantReserveBeforeFirstSlotTime() {
        val scheduler = createScheduler(6, 0)
        scheduler.reserve(1, 1, time(7, Scheduler.firstSlotTime - Scheduler.slotDuration))
    }

    @Test(expectedExceptions = [ClientException::class])
    fun cantReserveAfterLastSlotTime() {
        val scheduler = createScheduler(6, 0)
        scheduler.reserve(1, 1, time(7, Scheduler.lastSlotTime + Scheduler.slotDuration))
    }

    @Test(expectedExceptions = [ClientException::class])
    fun cantReserveSlotSoonerThan24Hours() {
        val scheduler = createScheduler(6, Scheduler.firstSlotTime + 1)
        scheduler.reserve(1, 1, time(7, Scheduler.firstSlotTime))
    }

    @Test
    fun timeShouldBeInNextWeek() {
        val scheduler = createScheduler(4, 0)
        assertThrows(ClientException::class.java)  {  scheduler.reserve(1, 1, time(6, Scheduler.firstSlotTime))  }
        assertThrows(ClientException::class.java)  {  scheduler.reserve(1, 1, time(14, Scheduler.firstSlotTime))  }
        scheduler.reserve(1, 1, time(7, Scheduler.firstSlotTime))
        scheduler.reserve(1, 1, time(13, Scheduler.firstSlotTime))
    }

    @Test
    fun cantReserveTwiceForSameFlat() {
        val scheduler = createScheduler(0, 0)
        scheduler.reserve(1, 1, time(7, Scheduler.firstSlotTime))
        assertThrows(ClientException::class.java)  {  scheduler.reserve(1, 1, time(7, Scheduler.firstSlotTime))  }
        assertThrows(ClientException::class.java)  {  scheduler.reserve(1, 2, time(7, Scheduler.firstSlotTime))  }
    }


    @Test
    fun approve() {
        val scheduler = createScheduler(0, 0)
        scheduler.reserve(1, 1, time(7, Scheduler.firstSlotTime))
        scheduler.approve(1, time(7, Scheduler.firstSlotTime))
    }

    @Test(expectedExceptions = [ClientException::class])
    fun cantApproveNonexistent() {
        val scheduler = createScheduler(0, 0)
        scheduler.approve(1, time(7, Scheduler.firstSlotTime))
    }

    @Test
    fun cantApproveTwice() {
        val scheduler = createScheduler(0, 0)
        scheduler.reserve(1, 1, time(7, Scheduler.firstSlotTime))
        scheduler.approve(1, time(7, Scheduler.firstSlotTime))
        assertThrows(ClientException::class.java)  {  scheduler.approve(1, time(7, Scheduler.firstSlotTime))  }
    }

    @Test
    fun cantReserveOrRejectApproved() {
        val scheduler = createScheduler(0, 0)
        scheduler.reserve(1, 1, time(7, Scheduler.firstSlotTime))
        scheduler.approve(1, time(7, Scheduler.firstSlotTime))
        assertThrows(ClientException::class.java)  {  scheduler.reserve(1, 1, time(7, Scheduler.firstSlotTime))  }
        assertThrows(ClientException::class.java)  {  scheduler.reject(1, time(7, Scheduler.firstSlotTime))  }
    }


    @Test
    fun reject() {
        val scheduler = createScheduler(0, 0)
        scheduler.reserve(1, 1, time(7, Scheduler.firstSlotTime))
        scheduler.reject(1, time(7, Scheduler.firstSlotTime))
    }

    @Test(expectedExceptions = [ClientException::class])
    fun cantRejectNonexistent() {
        val scheduler = createScheduler(0, 0)
        scheduler.reject(1, time(7, Scheduler.firstSlotTime))
    }

    @Test
    fun cantRejectTwice() {
        val scheduler = createScheduler(0, 0)
        scheduler.reserve(1, 1, time(7, Scheduler.firstSlotTime))
        scheduler.reject(1, time(7, Scheduler.firstSlotTime))
        assertThrows(ClientException::class.java)  {  scheduler.reject(1, time(7, Scheduler.firstSlotTime))  }
    }

    @Test
    fun cantReserveOrApproveRejected() {
        val scheduler = createScheduler(0, 0)
        scheduler.reserve(1, 1, time(7, Scheduler.firstSlotTime))
        scheduler.reject(1, time(7, Scheduler.firstSlotTime))
        assertThrows(ClientException::class.java)  {  scheduler.reserve(1, 1, time(7, Scheduler.firstSlotTime))  }
        assertThrows(ClientException::class.java)  {  scheduler.approve(1, time(7, Scheduler.firstSlotTime))  }
    }


    @Test
    fun cancelReserved() {
        val scheduler = createScheduler(0, 0)
        scheduler.reserve(1, 1, time(7, Scheduler.firstSlotTime))
        scheduler.cancel(1, 1, time(7, Scheduler.firstSlotTime))
    }

    @Test
    fun cancelApproved() {
        val scheduler = createScheduler(0, 0)
        scheduler.reserve(1, 1, time(7, Scheduler.firstSlotTime))
        scheduler.approve(1, time(7, Scheduler.firstSlotTime))
        scheduler.cancel(1, 1, time(7, Scheduler.firstSlotTime))
    }

    @Test
    fun cantCancelRejected() {
        val scheduler = createScheduler(0, 0)
        scheduler.reserve(1, 1, time(7, Scheduler.firstSlotTime))
        scheduler.reject(1, time(7, Scheduler.firstSlotTime))
        assertThrows(ClientException::class.java)  {  scheduler.cancel(1, 1, time(7, Scheduler.firstSlotTime))  }
    }

    @Test(expectedExceptions = [ClientException::class])
    fun cantCancelNonexistent() {
        val scheduler = createScheduler(0, 0)
        scheduler.cancel(1, 1, time(7, Scheduler.firstSlotTime))
    }

    @Test
    fun cantCancelOtherVisitorsSlot() {
        val scheduler = createScheduler(0, 0)
        scheduler.reserve(1, 2, time(7, Scheduler.firstSlotTime))
        assertThrows(ClientException::class.java)  {  scheduler.cancel(1, 1, time(7, Scheduler.firstSlotTime))  }
    }


}