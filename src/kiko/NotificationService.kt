package kiko


class NotificationService {
    fun notifyVisitRequest(flatId: Int, visitorId: Int, time: Long) {}
    fun notifyVisitApproved(flatId: Int, visitorId: Int, time: Long) {}
    fun notifyVisitRejected(flatId: Int, visitorId: Int, time: Long) {}
    fun notifyVisitCanceled(flatId: Int, visitorId: Int, time: Long) {}
}