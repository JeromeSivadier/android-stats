package com.example.android.stats.calls

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import android.view.View
import androidx.core.app.ActivityCompat.requestPermissions
import androidx.core.content.PermissionChecker
import com.example.android.stats.*
import java.time.LocalDateTime

class CallsStats(private val context: Context) : StatsProvider<IndividualCallStats> {
    companion object {
        const val PERMISSIONS_CODE = 123
    }

    override fun getPageTitle(): String {
        return context.getString(R.string.calls_page_title)
    }

    override fun checkRuntimePermissions(): Boolean {
        return PermissionChecker.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) == PermissionChecker.PERMISSION_GRANTED
    }

    override fun requestPermissions() {
        requestPermissions(context as Activity, arrayOf(Manifest.permission.READ_CALL_LOG), PERMISSIONS_CODE)
    }

    override fun onRuntimePermissionsUpdated(requestCode: Int, permissions: Array<out String>, grantResults: IntArray): Boolean {
        return requestCode == PERMISSIONS_CODE && grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }
    }

    override fun getMissingPermissionsMessage(): String {
        return context.getString(R.string.missing_permissions).format("READ_CALL_LOG")
    }

    override suspend fun getDataForRange(range: Pair<LocalDateTime, LocalDateTime>): List<IndividualCallStats> {
        val callLogs = getCallLogs(context, startDate = range.first, endDate = range.second)
        return generateStats(callLogs)
            .sortedBy(IndividualCallStats::totalTime)
            // If list > n items, merge the first ones (since list is ordered by biggest total time at the end, we only keep the biggest ones)
            .nLast(15) { left, right ->
                val mergedStats = IndividualCallStats("Other")
                (right.calls + left.calls).forEach { mergedStats.addCall(it) }
                mergedStats
            }
    }

    override fun getTotalText(): String {
        return context.getString(R.string.calls_total)
    }

    override fun getTotalIcon(): Int {
        return android.R.drawable.ic_menu_call
    }

    override fun getStatsText(): String {
        return context.getString(R.string.calls_stats_title)
    }

    override fun computeTotal(data: List<IndividualCallStats>): String {
        return data.map { it.totalTime }.sum().toPrettyDuration()
    }

    override fun getXValues(data: List<IndividualCallStats>): List<String> {
        return data.map(IndividualCallStats::name)
    }

    override fun dataToX(): (data: IndividualCallStats) -> String = IndividualCallStats::name
    override fun dataToY(): (data: IndividualCallStats) -> Float = { it.totalTime.toFloat() }

    override fun formatY(x: Float): String {
        return x.toLong().toPrettyDuration(false)
    }

    override fun getDetailedStatsLayout(): Int {
        return R.layout.calls_detailed_stats
    }

    override fun showDetailedStats(v: View, selected: IndividualCallStats) {
        v.findTextView(R.id.detailed_stats_label).text = context.getString(R.string.detailed_stats_for).format(selected.name)

        val outgoingCalls = countTypeAndDuration(selected.calls, CallLog.Calls.OUTGOING_TYPE)
        v.findTextView(R.id.outgoing_calls_number).text = outgoingCalls.first.toString()
        v.findTextView(R.id.outgoing_calls_duration).text = outgoingCalls.second.toPrettyDuration()

        val incomingCalls = countTypeAndDuration(selected.calls, CallLog.Calls.INCOMING_TYPE)
        v.findTextView(R.id.incoming_calls_number).text = incomingCalls.first.toString()
        v.findTextView(R.id.incoming_calls_duration).text = incomingCalls.second.toPrettyDuration()

        val missedCalls = selected.calls.filter { it.callType == CallLog.Calls.MISSED_TYPE }
        v.findTextView(R.id.missed_calls_number).text = missedCalls.size.toString()
    }

    private fun countTypeAndDuration(calls: List<CallLogInfo>, wantedType: Int): Pair<Int, Long> {
        val byType = calls.filter { it.callType == wantedType }
        return Pair(byType.size, byType.fold(0L) { sum, e -> sum + e.duration })
    }
}