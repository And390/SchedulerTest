package kiko


interface TimeService {
    fun now(): Long
}

class RealTimeService : TimeService {
    override fun now() = System.currentTimeMillis()
}

class FakeTimeService(private val fakeNow: Long) : TimeService {
    override fun now() = fakeNow
}