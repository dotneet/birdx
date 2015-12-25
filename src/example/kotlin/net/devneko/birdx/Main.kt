package net.devneko.birdx

import io.vertx.core.Vertx
import io.vertx.kotlin.lang.json.object_

class MyController(handler: RequestHandler) : RequestHandler by handler {

    companion object Router {
        fun new(v:RequestHandler) = MyController(v)
        fun router() = BirdxRouter.new {
            get("/controller", { new(this).index() })
        }
    }

    fun index() {
        replyText("controller index")
    }
}

class MyAppService(val vertx:Vertx) {
    fun execute(param:String, resultHandler:(String)->Unit) {
        // 時間のかかる処理を非同期で実行
        vertx.executeBlocking<String>({ h ->
            Thread.sleep(1000);
            val result = param + param
            h.complete(result)
        },{ asyncResult ->
            // 結果をコントローラに返す
            resultHandler(asyncResult.result())
        })
    }
}

fun main(args : Array<String>) {

    val birdx = Birdx {
        router {
            get("/") {
                // パラメータを渡してサービスを実行
                MyAppService(vertx).execute(param("msg")) { result ->
                    replyText(result)
                }
            }
        }
    }

    // コントローラを追加できる
    val r1 = BirdxRouter()
    r1.get("/add",{
        replyJson {
            object_("a" to "b")
        }
    })
    birdx.addRouter(r1)

    // よくあるコントローラクラスみたいのも使える
    birdx.addRouter(MyController.router())

    // サーバー起動
    birdx.start(8080)
}


