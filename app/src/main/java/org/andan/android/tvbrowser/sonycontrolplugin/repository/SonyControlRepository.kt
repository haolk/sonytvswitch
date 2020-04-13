package org.andan.android.tvbrowser.sonycontrolplugin.repository

import android.util.Log
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.andan.android.tvbrowser.sonycontrolplugin.SonyControlApplication
import org.andan.android.tvbrowser.sonycontrolplugin.datastore.ControlPreferenceStore
import org.andan.android.tvbrowser.sonycontrolplugin.domain.SonyControl
import org.andan.android.tvbrowser.sonycontrolplugin.domain.SonyControls
import org.andan.android.tvbrowser.sonycontrolplugin.domain.SonyProgram
import org.andan.android.tvbrowser.sonycontrolplugin.network.*
import retrofit2.Response
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.util.regex.Pattern
import javax.inject.Inject

class SonyControlRepository @Inject constructor(val client: OkHttpClient, val api: SonyService, val preferenceStore: ControlPreferenceStore) {
    private val TAG = SonyControlRepository::class.java.name

    var sonyControls = MutableLiveData<SonyControls>()
    var selectedSonyControl = MutableLiveData<SonyControl>()
    val sonyServiceContext = SonyControlApplication.get().appComponent.sonyServiceContext()

    companion object {
        const val SUCCESS_CODE = 0
        const val ERROR_CODE = -1
    }

    init {
        Log.d(TAG, "init")
        sonyControls.value = preferenceStore.loadControls()
        sonyServiceContext.sonyService = api
        selectedSonyControl.value = getSelectedControl()
        onSonyControlsChange()
    }

    private val _responseMessage = MutableLiveData<Event<String>>()
    val responseMessage: LiveData<Event<String>>
        get() = _responseMessage

    val gson = GsonBuilder().create()

    private fun onSonyControlsChange() {
        sonyServiceContext.sonyService = api
        if(selectedSonyControl.value != null) {
            Log.d(TAG, "onSonyControlsChange(): ${selectedSonyControl.value}")
            sonyServiceContext.ip = selectedSonyControl.value!!.ip
            sonyServiceContext.uuid = selectedSonyControl.value!!.uuid
            sonyServiceContext.nickname = selectedSonyControl.value!!.nickname
            sonyServiceContext.devicename = selectedSonyControl.value!!.devicename
        }
    }

    private fun <T> MutableLiveData<T>.notifyObserver() {
        this.value = this.value
    }

    private fun <T> MutableLiveData<T>.notifyObserverBackground() {
        this.postValue(this.value)
    }

    private fun getSelectedControl() : SonyControl? {
        return if(sonyControls.value!!.selected >= 0 && sonyControls.value!!.selected <= sonyControls.value!!.controls.size-1) {
            sonyControls.value!!.controls[sonyControls.value!!.selected]
        } else null
    }

   /* private val _playingContentInfo = MutableLiveData<PlayingContentInfoResponse>()
    val playingContentInfo: LiveData<PlayingContentInfoResponse> = _playingContentInfo

    var playingContentInfo2 : PlayingContentInfoResponse = PlayingContentInfoResponse.notAvailableValue*/

    suspend inline fun <reified T> avContentService(jsonRpcRequest: JsonRpcRequest): Resource<T> {
        return apiCall(call = { api.sonyRpcService("http://" + selectedSonyControl.value?.ip + SONY_AV_CONTENT_ENDPOINT, jsonRpcRequest) })
    }

    suspend inline fun <reified T> accessControlService(jsonRpcRequest: JsonRpcRequest): Resource<T> {
        return apiCall(call = { api.sonyRpcService("http://" + selectedSonyControl.value?.ip + SONY_ACCESS_CONTROL_ENDPOINT, jsonRpcRequest) })
    }

    suspend inline fun <reified T> systemService(jsonRpcRequest: JsonRpcRequest): Resource<T> {
        return apiCall(call = { api.sonyRpcService("http://" + selectedSonyControl.value?.ip + SONY_SYSTEM_ENDPOINT, jsonRpcRequest) })
    }

    suspend fun setWolMode(enabled: Boolean) {
        val resource = systemService<Any>(JsonRpcRequest.setWolMode(enabled))
        if(resource.status==Status.ERROR) {
            _responseMessage.postValue(Event(resource.message))
        }
    }

    suspend fun fetchWolMode() {
        val resource = systemService<WolModeResponse>(JsonRpcRequest.getWolMode())
        if(resource.status==Status.SUCCESS) {
            getSelectedControl()!!.systemWolMode = resource.data!!.enabled
            saveControls(true)
        } else {
            _responseMessage.postValue(Event(resource.message))
        }
    }

    suspend fun setPowerSavingMode(mode: String) {
        val resource = systemService<Any>(JsonRpcRequest.setPowerSavingMode(mode))
        if(resource.status==Status.ERROR) {
            _responseMessage.postValue(Event(resource.message))
        }
    }


    suspend fun fetchRemoteControllerInfo() {
        val resource = systemService<Array<RemoteControllerInfoItemResponse>>(JsonRpcRequest.getRemoteControllerInfo())
        Log.d(TAG, "remoteControllerInfo(): ${getSelectedControl()!!.toString()}")
        if(resource.status==Status.SUCCESS) {
            getSelectedControl()!!.commandList = LinkedHashMap()
            for(remoteControllerInfoItem in resource.data!!) {
                getSelectedControl()!!.commandList[remoteControllerInfoItem.name] = remoteControllerInfoItem.value
            }
            saveControls(true)
        } else {
            _responseMessage.postValue(Event(resource.message))
        }
    }

    suspend fun fetchSystemInformation() {
        val resource = systemService<SystemInformationResponse>(JsonRpcRequest.getSystemInformation())
        Log.d(TAG, "remoteControllerInfo(): ${getSelectedControl()!!.toString()}")
        if(resource.status==Status.SUCCESS) {
            getSelectedControl()!!.systemName = resource.data!!.name
            getSelectedControl()!!.systemProduct = resource.data!!.product
            getSelectedControl()!!.systemModel = resource.data!!.model
            getSelectedControl()!!.systemMacAddr = resource.data!!.macAddr
            saveControls(true)
        } else {
            _responseMessage.postValue(Event(resource.message))
        }
    }

    suspend fun getPlayingContentInfo(): PlayingContentInfoResponse {
        val resource = avContentService<PlayingContentInfoResponse>(JsonRpcRequest.getPlayingContentInfo())
        PlayingContentInfoResponse.notAvailableValue
        return if (resource.status == Status.ERROR) {
            _responseMessage.postValue(Event(resource.message))
            PlayingContentInfoResponse.notAvailableValue
        } else {
            resource.data!!
        }
    }

    suspend fun setPlayContent(uri: String): Int {
        val resource = avContentService<Unit>(JsonRpcRequest.setPlayContent(uri))
        if (resource.status == Status.ERROR) {
            _responseMessage.postValue(Event(resource.message))
            return ERROR_CODE
        }
        return SUCCESS_CODE
    }

    suspend fun fetchSourceList() {
        val resource = avContentService<Array<SourceListItemResponse>>(
            JsonRpcRequest.getSourceList("tv"))
        if(resource.status==Status.SUCCESS) {
            getSelectedControl()!!.sourceList = mutableListOf()
            for(sourceItem in resource.data!!) {
                if (sourceItem.source == "tv:dvbs") {
                    getSelectedControl()!!.sourceList.add(sourceItem.source + "#general")
                    getSelectedControl()!!.sourceList.add(sourceItem.source + "#preferred")
                } else {
                    getSelectedControl()!!.sourceList.add(sourceItem.source)
                }
            }
            saveControls(true)
        } else {
            _responseMessage.postValue(Event(resource.message))
        }
    }

    suspend fun fetchProgramList() {
        Log.d(TAG, "fetchProgramList(): ${getSelectedControl()!!.sourceList}")
        if(getSelectedControl()!!.sourceList.isNullOrEmpty()) {
            fetchSourceList()
        }
        getSelectedControl()!!.programList.clear()
        val programList = mutableListOf<SonyProgram>()
        if (!getSelectedControl()!!.sourceList.isNullOrEmpty()) {
            for (sonySource in getSelectedControl()!!.sourceList) {
                // get programs in pages
                var stidx = 0
                var count = 0
                while(fetchTvContentList(sonySource, stidx, SonyControl.PAGE_SIZE, programList).let {
                        count = it; it>0
                    }) { stidx += SonyControl.PAGE_SIZE }
                // Break loop over source in case of error
                if(count == -1) {
                    Log.d(TAG, "fetchProgramList(): error")
                }
            }
            getSelectedControl()!!.programList.clear()
            getSelectedControl()!!.programList.addAll(programList)
            saveControls(true)
            _responseMessage.postValue(Event("Fetched ${getSelectedControl()!!.programList.size} programs from TV"))
            Log.d(TAG, "fetchProgramList(): ${getSelectedControl()!!.programList.size}")
        }
    }

    private suspend fun fetchTvContentList(sourceType: String, stIdx: Int, cnt: Int, plist: MutableList<SonyProgram>): Int {
        val sourceSplit = sourceType.split("#").toTypedArray()
        val source = sourceSplit[0]
        var type = ""
        if (sourceSplit.size > 1) type = sourceSplit[1]
        val resource = avContentService<Array<ContentListItemResponse>>(JsonRpcRequest.getContentList(source, stIdx, cnt, type))
        return if(resource.status==Status.SUCCESS) {
            for(sonyProgramResponse in resource.data!!) {
                if (sonyProgramResponse.programMediaType.equals("tv", true)
                    && sonyProgramResponse.title != "." && !sonyProgramResponse.title.isEmpty()
                    && !sonyProgramResponse.title.contains("TEST")) {
                    val sonyProgram = sonyProgramResponse.asDomainModel()
                    sonyProgram.source = source
                    plist.add(sonyProgram)
                }
            }
            resource.data!!.size
        } else {
            _responseMessage.postValue(Event(resource.message))
            -1
        }
    }

    suspend fun registerControl(challenge: String?) {
        withContext(Dispatchers.IO) {
            selectedSonyControl.value?.let {
                Log.d(TAG, "registerControl(): ${sonyServiceContext.nickname}")
                if(challenge != null) {
                    sonyServiceContext.password=challenge
                }
                try {
                    val response = api.sonyRpcService(
                        "http://" + it.ip + SONY_ACCESS_CONTROL_ENDPOINT, JsonRpcRequest.actRegister(it.nickname, it.devicename, it.uuid)
                    )
                    // update token
                    if (response.isSuccessful) {
                        val jsonRpcResponse = response.body()
                        if (jsonRpcResponse?.error != null) {
                            _responseMessage.postValue(Event(jsonRpcResponse.error.asJsonArray.get(1).asString))
                        } else if (!response.headers()["Set-Cookie"].isNullOrEmpty()) {
                            // get token from set cookie and store
                            val cookieString: String? = response.headers()["Set-Cookie"]
                            val pattern = Pattern.compile("auth=([A-Za-z0-9]+)")
                            val matcher = pattern.matcher(cookieString)
                            if (matcher.find()) {
                                preferenceStore.storeToken(it.uuid, "auth=" + matcher.group(1))
                            }
                        }
                    } else {
                        if (response.code() == HttpURLConnection.HTTP_UNAUTHORIZED) {
                            // Navigate to enter challenge code view
                            _responseMessage.postValue(Event(response.message()))
                        } else {
                            _responseMessage.postValue(Event(response.message()))
                        }
                    }
                } catch (se: SocketTimeoutException) {
                    Log.e(TAG, "Error: ${se.message}")
                    _responseMessage.postValue(Event(se.message?: "Unknown exception in registerControl"))
                }
                sonyServiceContext.password=""
            }
        }
    }

    suspend fun sendIRCC(code: String) {
        withContext(Dispatchers.IO) {
            selectedSonyControl.value?.let {
                val requestBodyText =
                    SONY_IRCC_REQUEST_TEMPLATE.replace("<IRCCCode>", "<IRCCCode>$code")

                val requestBody: RequestBody =
                    requestBodyText.toRequestBody("text/xml".toMediaTypeOrNull())
                Log.d(TAG, "sendIRCC: $requestBodyText")
                val response = api.sendIRCC("http://" + it.ip + SONY_IRCC_ENDPOINT, requestBody)
                if (!response.isSuccessful) {
                    _responseMessage.postValue(Event(response.message()))
                }
            }
        }
    }

    suspend fun wakeOnLan() {
        withContext(Dispatchers.IO) {
            selectedSonyControl.value?.let {
                WakeOnLan.wakeOnLan(getSelectedControl()!!.ip, getSelectedControl()!!.systemMacAddr)
            }
        }
    }

    suspend fun ssd() {
        withContext(Dispatchers.IO) {
            selectedSonyControl.value?.let {
                val ipAndDeviceList = SSDP.getSonyIpAndDeviceList()
                Log.d(TAG, "ssd() $ipAndDeviceList")
                //WakeOnLan.wakeOnLan(getSelectedControl()!!.ip, getSelectedControl()!!.systemMacAddr)
            }
        }
    }

    fun updateChannelMapsFromChannelNameList(channelNameList: List<String>) {
        var isUpdated = false
        for (control in sonyControls.value!!.controls) {
            Log.d(TAG, "updateChannelMapsFromChannelNameList: ${channelNameList.size} ${control.channelProgramMap.size}")
            for (channelName in channelNameList) {
                // create mapping entry for unmapped channels
                if (!control.channelProgramMap.containsKey(channelName)) {
                    Log.d(TAG, "updateChannelMapsFromChannelNameList: $channelName")
                    control.channelProgramMap[channelName] = ""
                    isUpdated = true
                }
                //ToDo: Handle deletion of channels
            }
        }
        if(isUpdated) saveControls()
        Log.d(TAG, "updateChannelMapsFromChannelNameList: finished")
    }

    suspend inline fun <reified T> apiCall(call: () -> Response<JsonRpcResponse>): Resource<T> {
        //val response: Response<T>
        return try {
            val response = call.invoke()
            if (response.isSuccessful) {
                val jsonRpcResponse = response.body()
                when {
                    jsonRpcResponse?.error != null -> {
                        Log.d("apiCall", "evaluate error")
                        Resource.Error(
                            jsonRpcResponse.error.asJsonArray.get(1).asString, response.code()
                        )
                    }
                    else -> {
                        Log.d("apiCall", "evaluate result")
                        Resource.Success(
                            response.code(),
                            gson.fromJson(
                                when {
                                    jsonRpcResponse?.result?.asJsonArray?.size() == 0 -> {
                                        JsonObject()
                                    }
                                    jsonRpcResponse?.result?.asJsonArray?.size()!! > 1 -> {
                                        jsonRpcResponse.result.asJsonArray?.get(1)
                                    }
                                    else -> {
                                        when (jsonRpcResponse.result.asJsonArray?.get(0)) {
                                            is JsonObject -> jsonRpcResponse.result.asJsonArray?.get(0)!!.asJsonObject
                                            is JsonArray -> jsonRpcResponse.result.asJsonArray?.get(0)!!.asJsonArray
                                            else -> jsonRpcResponse.result.asJsonArray.get(0).asJsonObject
                                        }
                                    }
                                }, T::class.java
                            )
                        )
                    }
                }
            } else {
                Log.d("apiCall", "evaluate response unsuccessful")
                Resource.Error(response.message(), response.code())
            }

        } catch (e: Exception) {
            e.printStackTrace()
            Log.d("apiCall", "evaluate exception ${e.message}")
            return Resource.Error(e.message ?: "Unknown apiCall exception", 0)
        }
    }

    fun saveControls() {
        saveControls(false)
    }

    fun saveControls(fromBackground: Boolean) {
        preferenceStore.storeControls(sonyControls.value!!)
        if(fromBackground) {
            sonyControls.notifyObserverBackground()
            selectedSonyControl.postValue(getSelectedControl())
        }
        else {
            sonyControls.notifyObserver()
            selectedSonyControl.value = getSelectedControl()
        }
        onSonyControlsChange()
    }

    fun addControl(control: SonyControl) {
        sonyControls.value!!.controls.add(control)
        sonyControls.value!!.selected = sonyControls.value!!.controls.size-1
        Log.d(TAG, "addControl: #${sonyControls.value!!.selected} $control")
        saveControls()
    }

    fun removeControl(index: Int): Boolean {
        if(index >=0 && index < sonyControls.value!!.controls.size) {

            var newSelected = sonyControls.value!!.selected -1
            if  (sonyControls.value!!.selected == 0 && sonyControls.value!!.controls.size > 1)
            {
                newSelected = sonyControls.value!!.controls.size - 2
            }
            sonyControls.value!!.controls.removeAt(index)
            sonyControls.value!!.selected = newSelected
            saveControls()
            return true
        }
        return false
    }

    fun setSelectedControlIndex(index : Int): Boolean {
        if(index < sonyControls.value!!.controls.size) {
            sonyControls.value!!.selected = index
            saveControls()
            return true
        }
        return false
    }
}

open class Event<out T>(private val content: T? = null) {

    var hasBeenHandled = false
        private set // Allow external read but not write

    /**
     * Returns the content and prevents its use again.
     */
    fun getContentIfNotHandled(): T? = if (hasBeenHandled) {
        null
    } else {
        hasBeenHandled = true
        content
    }

    /**
     * Returns the content, even if it's already been handled.
     */
    fun peekContent(): T? = content
}

class EventObserver<T>(private val onEventUnhandledContent: (T) -> Unit) : Observer<Event<T>> {
    override fun onChanged(event: Event<T>?) {
        event?.getContentIfNotHandled()?.let { value ->
            onEventUnhandledContent(value)
        }
    }
}

fun <T> LiveData<out Event<T>>.observeEvent(owner: LifecycleOwner, onEventUnhandled: (T) -> Unit) {
    observe(owner, Observer { it?.getContentIfNotHandled()?.let(onEventUnhandled) })
}