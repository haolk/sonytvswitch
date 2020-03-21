package org.andan.android.tvbrowser.sonycontrolplugin.viewmodels

import android.util.Log
import androidx.lifecycle.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.andan.android.tvbrowser.sonycontrolplugin.SonyControlApplication
import org.andan.android.tvbrowser.sonycontrolplugin.di.DaggerApplicationComponent
import org.andan.android.tvbrowser.sonycontrolplugin.domain.SonyControl
import org.andan.android.tvbrowser.sonycontrolplugin.domain.SonyProgram2
import org.andan.android.tvbrowser.sonycontrolplugin.network.PlayingContentInfoResponse
import org.andan.android.tvbrowser.sonycontrolplugin.repository.SonyRepository

class TestViewModel : ViewModel() {
    // TODO: Implement the ViewModel
    private val TAG = TestViewModel::class.java.name
    // repository for control data
    private val repository: SonyRepository = SonyControlApplication.get().appComponent.sonyRepository()
    // val sampleText = "This is a sample text"

    /*val powerStatus : LiveData<String> = liveData(Dispatchers.IO) {
        val result = repository.getPowerStatus()
        emit(result)
    }*/

   /* fun getCurrentTime() = liveData(Dispatchers.IO) {
        //val result = repository.getCurrentTime()
        // Log.d(TAG,"currentTime: " + result)
        emit(repository.getCurrentTime())
    }*/

    private var _selectedSonyControl = MutableLiveData<SonyControl>()
    val selectedSonyControl: LiveData<SonyControl>
        get() = _selectedSonyControl

    init {
        _selectedSonyControl = repository.selectedSonyControl
    }

    fun getSelectedControl(): SonyControl? {
        return selectedSonyControl.value
    }

    private var programSearchQuery: String? = null

    private var filteredProgramList = MutableLiveData<List<SonyProgram2>>()

    // derived variables for selected control
    private var programChannelMap: MutableMap<String, String> = HashMap()
    var uriProgramMap: MutableMap<String, SonyProgram2> = HashMap()
    var programTitleList: MutableList<String> = ArrayList()
    var selectedChannelName: String = ""

    //var activeContentInfo = MutableLiveData<PlayingContentInfoResponse>()
    private var currentProgram: SonyProgram2? = null
    var lastProgram: SonyProgram2? = null

    private var channelNameList: MutableList<String> = ArrayList()
    private var filteredChannelNameList = MutableLiveData<List<String>>()
    private var channelNameSearchQuery: String? = null

    fun updateCurrentProgram(program: SonyProgram2) {
        if(currentProgram?.uri!=program.uri) {
            lastProgram = currentProgram
            currentProgram = program
            Log.d(TAG, "updateCurrentProgram ${lastProgram?.title} ${currentProgram?.title} ${program.title}"
            )
        }
    }

    fun onSelectedIndexChange() {
        lastProgram = null
        currentProgram = null
        //activeContentInfo.value = noActiveProgram
        refreshDerivedVariablesForSelectedControl()
        filterProgramList(null)
        //filterChannelNameList(null)
    }

    fun refreshDerivedVariablesForSelectedControl() {
        Log.d(TAG,"refreshDerivedVariablesForSelectedControl()")
        programTitleList.clear()
        //channelNameList.clear()
        uriProgramMap.clear()
        if (getSelectedControl()?.programList != null) {
            if (getSelectedControl()?.channelProgramMap != null) {
                for (mappedChannelName in getSelectedControl()?.channelProgramMap!!.keys) {
                    channelNameList.add(mappedChannelName)
                    val programUri = getSelectedControl()!!.channelProgramMap[mappedChannelName]
                    if (programUri != null && getSelectedControl()!!.programUriMap!!.containsKey(programUri)) {
                        programChannelMap[programUri] = mappedChannelName
                    }
                }
            }

            for (program in getSelectedControl()!!.programList) {
                programTitleList.add(program.title)
                uriProgramMap[program.uri]=program
            }
        }
    }

    fun getFilteredProgramList(): MutableLiveData<List<SonyProgram2>> {
        return filteredProgramList
    }


    fun filterProgramList(query: String?) {
        Log.d(TAG,"filterProgramList(query: String?)")
        Log.d(TAG, "filter program list $query ")
        if(getSelectedControl()?.programList!=null) {
            programSearchQuery = query
            filteredProgramList.value = getSelectedControl()!!.programList.filter { p ->
                programSearchQuery.isNullOrEmpty() || p.title.contains(
                    programSearchQuery!!,
                    true
                )
            }
        } else {
            filteredProgramList.value = ArrayList()
        }
    }

    fun getProgramSearchQuery(): String? {
        Log.d(TAG,"getProgramSearchQuery()")
        return programSearchQuery
    }

    fun getChannelForProgramUri(uri: String): String {
        //Log.d(TAG,"getChannelForProgramUri(uri: String)")
        return programChannelMap[uri] ?: ""
    }

    val playingContentInfo = repository.playingContentInfo
    val noPlayingContentInfo = PlayingContentInfoResponse("","","","","","","",0)
    fun fetchPlayingContentInfo() = viewModelScope.launch(Dispatchers.IO) {
        //val result = repository.getCurrentTime()
        // Log.d(TAG,"currentTime: " + result)
        repository.getPlayingContentInfo()
    }

    fun setPlayContent(uri: String) = viewModelScope.launch(Dispatchers.IO) {
        repository.setPlayContent(uri)
    }


    fun addControl(control: SonyControl) {
        repository.addControl(control)
    }

    //fun registerControl()

//    fun getCurrentTime() = repository.getCurrentTime()

//val currentTime = repository.currentTimeString

//fun getCurrentTime(): LiveData<String> = repository.getCurrentTime()

//val currentTimeString = getCurrentTime()
}