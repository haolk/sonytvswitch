package org.andan.android.tvbrowser.sonycontrolplugin.domain

import me.xdrop.fuzzywuzzy.Applicable
import me.xdrop.fuzzywuzzy.FuzzySearch
import me.xdrop.fuzzywuzzy.ToStringFunction
import me.xdrop.fuzzywuzzy.algorithms.TokenSet
import me.xdrop.fuzzywuzzy.model.BoundExtractedResult
import java.util.*

object ChannelNameFuzzyMatch {
    private val tokenSet: Applicable = TokenSet()
    private val toStringFunction: ToStringFunction<String> =
        NormalizeString()

    fun matchTop(
        channelName: String,
        channelNameList: List<String>,
        ntop: Int,
        regExMatch: Boolean
    ): Set<Int> {
        val topChannelNameToChannelNameListMatchIndexSet: MutableSet<Int> =
            LinkedHashSet()
        val cs = channelName.toLowerCase()
        val whiteSpaceIndex = channelName.indexOf(" ")
        var cs1: String? = null
        if (whiteSpaceIndex > 0) {
            cs1 = channelName.toLowerCase().substring(0, whiteSpaceIndex)
        }
        var numberMatches = 0
        if (regExMatch) {
            for (i in channelNameList.indices) {
                val ps = channelNameList[i].toLowerCase()
                if (ps.matches("$cs\\b.*".toRegex())) {
                    topChannelNameToChannelNameListMatchIndexSet.add(i)
                    numberMatches++
                    if (numberMatches == ntop) break
                }
            }
            if (numberMatches < ntop && cs1 != null && topChannelNameToChannelNameListMatchIndexSet.size == 0) {
                for (i in channelNameList.indices) {
                    val ps = channelNameList[i].toLowerCase()
                    if (ps.matches("$cs1\\b.*".toRegex())) {
                        topChannelNameToChannelNameListMatchIndexSet.add(i)
                        numberMatches++
                        if (numberMatches == ntop) break
                    }
                }
            }
        }
        if (numberMatches < ntop) {
            val matches =
                FuzzySearch.extractTop(
                    channelName,
                    channelNameList,
                    toStringFunction,
                    tokenSet,
                    ntop
                )
            for (match in matches) {
                val index = match.index
                if (index >= 0) {
                    topChannelNameToChannelNameListMatchIndexSet.add(index)
                    numberMatches++
                    if (numberMatches == ntop) break
                }
            }
        }
        return topChannelNameToChannelNameListMatchIndexSet
    }

    fun matchOne(
        channelName: String,
        channelNameList: List<String>,
        regExMatch: Boolean
    ): Int {
        var index = -1
        val topChannelNameToChannelNAmeListMatchIndexSet =
            matchTop(
                channelName,
                channelNameList,
                1,
                regExMatch
            )
        val iter: Iterator<*> =
            topChannelNameToChannelNAmeListMatchIndexSet.iterator()
        if (iter.hasNext()) index = iter.next() as Int
        return index
    }

    fun mergeResults(
        matchList1: List<BoundExtractedResult<*>>?,
        matchList2: List<BoundExtractedResult<*>>?,
        size: Int
    ): List<BoundExtractedResult<*>> {
        val mergedList: MutableList<BoundExtractedResult<*>> = mutableListOf()
        mergedList.addAll(matchList1!!)
        mergedList.addAll(matchList2!!)
        Collections.sort(mergedList, Collections.reverseOrder())
        val indexSet = HashSet<Int>()
        val it = mergedList.iterator()
        while (it.hasNext()) {
            val match = it.next()
            val index = match.index
            if (indexSet.contains(index)) {
                it.remove()
            } else {
                indexSet.add(index)
            }
        }
        return mergedList.subList(0, Math.min(mergedList.size, size))
    }

    internal class NormalizeString : ToStringFunction<String> {
        override fun apply(s: String): String {
            return s.replace("_", "").toLowerCase()
        }
    }
}