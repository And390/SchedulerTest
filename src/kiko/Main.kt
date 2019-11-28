package kiko

import com.fasterxml.jackson.core.JsonProcessingException
import io.ktor.application.*
import io.ktor.features.ContentNegotiation
import io.ktor.features.StatusPages
import io.ktor.http.*
import io.ktor.jackson.jackson
import io.ktor.request.receive
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import org.slf4j.LoggerFactory


fun main()
{
    val timeService = RealTimeService()
    val dataAccess = DataAccess()
    val notificationService = NotificationService()
    val scheduler = Scheduler(timeService, dataAccess, notificationService)

    startServer(scheduler)
}

fun startServer(scheduler: Scheduler)
{
    val log = LoggerFactory.getLogger("main")
    val port = 8080
    val server = embeddedServer(Netty, port = port) {
        install(ContentNegotiation) {
            jackson {
            }
        }
        install(StatusPages) {
            exception<ClientException> { exception ->
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to (exception.message ?: "")))
            }
            exception<Exception> { exception ->
                log.error("", exception)
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to ("Internal Server Error")))
            }
        }
        routing {
            post("/schedule/{flatId}/{time}") {
                val flatId = call.parameters["flatId"]!!.let { try { it.toInt() } catch (e: NumberFormatException) { throw ClientException("Wrong flat id: $it") } }
                val time = call.parameters["time"]!!.let { try { it.toLong() } catch (e: NumberFormatException) { throw ClientException("Wrong time: $it") } }
                val request = try { call.receive<ScheduleRequest>() } catch (e: JsonProcessingException) { throw ClientException("Wrong JSON request") }
                if (request.action == null)  throw ClientException("No `action` in the request body")
                when (request.action) {
                    "reserve" -> scheduler.reserve(flatId, request.visitorId ?: throw ClientException("No `visitorId` in the request body"), time)
                    "approve" -> scheduler.approve(flatId, time)
                    "reject" -> scheduler.reject(flatId, time)
                    "cancel" -> scheduler.cancel(flatId, request.visitorId ?: throw ClientException("No `visitorId` in the request body"), time)
                    else -> throw ClientException("Unknown action")
                }
                call.respond(mapOf<Any,Any>())
            }
        }
    }
    server.start(wait = true)
}

class ScheduleRequest(
    val action: String?,
    val visitorId: Int?
) {
    constructor() : this(null, null)  //for Jackson
}