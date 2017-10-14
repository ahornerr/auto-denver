package codes.horner.whatthehackinference

import codes.horner.whatthehackinference.models.CarStateMessage
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import io.reactivex.functions.Function
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.ReplaySubject

/**
 * Created by Andy on 10/12/2017.
 */

class Serial {

    val perLineSubject: PublishSubject<String> = PublishSubject.create()

    val rawSubject: PublishSubject<String> = PublishSubject.create()

    val latestCar: ReplaySubject<CarStateMessage> = ReplaySubject.create(1)

    val sendQueue: PublishSubject<String> = PublishSubject.create()

    val gson = Gson()

    init {
        rawSubject.flatMapIterable(SplitMapper("\n")).subscribe {
            perLineSubject.onNext(it)

            if (it.startsWith("{")) {
                try {
                    val carMessage = gson.fromJson<CarStateMessage>(it, CarStateMessage::class.java)
//                    Log.d("Test", carMessage.toString())
                    latestCar.onNext(carMessage)
                } catch (e: JsonSyntaxException) {
                    e.printStackTrace()
                }
            }
        }
    }

    companion object {
        val Device = Serial()
    }
}

class SplitMapper(val split: String) : Function<String, MutableIterable<String>> {

    var last = ""

    override fun apply(str: String): MutableIterable<String> {
        val splitStr = str.split(split)
//        Log.d("Raw", splitStr.joinToString("|"))
        last += splitStr.first()
        if (splitStr.size > 1) {
            val subList = splitStr.slice(IntRange(1, splitStr.size - 2))
            val list = arrayListOf(last, *subList.toTypedArray())
            last = splitStr.last()
            return list
        }
        return ArrayList()
    }

}
