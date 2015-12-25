package net.devneko.birdx

import io.vertx.core.Vertx
import io.vertx.core.VertxOptions
import io.vertx.core.http.HttpMethod
import io.vertx.core.http.HttpServerRequest
import io.vertx.core.http.HttpServerResponse
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.lang.*
import io.vertx.kotlin.lang.json.Json
import kotlinx.util.with
import java.util.*

interface Birdx {
    val vertx:Vertx;
    fun router(block:BirdxRouter.()->Unit)
    fun addRouter(router:BirdxRouter)
    fun start(port:Int,bind:String="0.0.0.0")
}

class RequestHandlerFactory(val block:RequestHandler.()->Unit) {
    fun createHandler(vertx:Birdx, request:HttpServerRequest):RequestHandler {
        return RequestHandlerImpl(request, vertx, block)
    }
}

interface RequestHandler {
    val request:HttpServerRequest
    val response:HttpServerResponse

    fun execute()

    // shortcut
    fun param(name:String) = request.getParam(name)
    fun header(name:String) = request.getHeader(name)
    fun replyText(text:String) {
        response.body {
            write(text)
        }
    }
    fun replyJson(block: Json.()->JsonObject) {
        response.bodyJson {
            block()
        }
    }
    fun body(block:HttpServerResponse.()->Unit) {
        response.body{ block() }
    }
}

class RequestHandlerImpl(
        override val request:HttpServerRequest,
        val birdx:Birdx,
        val block:RequestHandler.()->Unit
) : RequestHandler {
    override val response:HttpServerResponse = request.response()
    override fun execute() {
        block()
    }
}

class RouteMatch(val method: HttpMethod, val path:String)

class BirdxRouter() {
    val handlers = HashMap<RouteMatch,RequestHandlerFactory>()
    fun get(path:String,block:RequestHandler.()->Unit) {
        val handler = RequestHandlerFactory(block)
        handlers.put(RouteMatch(HttpMethod.GET,path), handler)
    }
    fun post(path:String,block:RequestHandler.()->Unit) {
        val handler = RequestHandlerFactory(block)
        handlers.put(RouteMatch(HttpMethod.POST,path), handler)
    }
    fun merge(router:BirdxRouter) {
        handlers.putAll(router.handlers)
    }
    fun resolve(request:HttpServerRequest):RequestHandlerFactory? {
        for ( entry in handlers.entries ) {
            if ( request.method().equals(entry.key.method) && request.path().equals(entry.key.path) ) {
                return entry.value
            }
        }
        return null
    }

    companion object Factory {
        fun new(block:BirdxRouter.()->Unit):BirdxRouter {
           return BirdxRouter().with(block)
        }
    }
}

class DefaultBirdx(override  val vertx:Vertx) : Birdx {
    val router = BirdxRouter()

    override fun router(block:BirdxRouter.()->Unit) {
        router.with{ block() }
    }
    override fun addRouter(router:BirdxRouter) {
        this.router.merge(router)
    }
    override fun start(port:Int,bind:String) {
        vertx.httpServer(port,bind) { request ->
            val handlerFactory = router.resolve(request)
            if ( handlerFactory == null ) {
                request.response {
                    body{
                        setStatus(404, "Not Found")
                        write("Not Found.")
                    }
                }
            } else {
                handlerFactory.createHandler(this@DefaultBirdx, request).execute()
            }
        }
    }
}

fun Birdx(block:Birdx.()->Unit):Birdx {
    val vertx = Vertx.vertx(VertxOptions())
    return DefaultBirdx(vertx).with {
        block()
    }
}

