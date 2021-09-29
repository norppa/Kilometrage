package com.ducksoup.kilometrage.fragment

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.ducksoup.kilometrage.DB
import com.ducksoup.kilometrage.DataViewModel
import com.ducksoup.kilometrage.DataViewModelFactory
import com.ducksoup.kilometrage.R
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.*


class GraphFragment : Fragment() {

    private val dataViewModel: DataViewModel by viewModels {
        DataViewModelFactory(DB.getDao(requireContext()))
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_graph, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)



        dataViewModel.allEntries.observe(viewLifecycleOwner, { observed ->
            observed?.let { recordsWithEntries ->
                val lineData = LineData()

                recordsWithEntries.forEachIndexed { index, recordWithEntries ->
                    var totalDistance = 0.0
                    val chartEntries = recordWithEntries.entries.mapNotNull map@{ entry ->
                        val timestamp = entry.date?.atZone(ZoneId.systemDefault())
                            ?.toEpochSecond()?.toFloat() ?: return@map null
                        totalDistance += entry.distance
                        Entry(timestamp, totalDistance.toFloat())
                    }
                    val dataSet = LineDataSet(chartEntries, recordWithEntries.record.name)
                    dataSet.color = color(index)
                    dataSet.lineWidth = 3F
                    lineData.addDataSet(dataSet)
                }
                val chart = view.findViewById<LineChart>(R.id.chart)
                chart.xAxis.position = XAxis.XAxisPosition.BOTTOM_INSIDE
                chart.xAxis.setDrawGridLines(false)
                chart.xAxis.valueFormatter = LineChartXAxisValueFormatter()
                chart.xAxis.isGranularityEnabled = true
                chart.xAxis.granularity = 86400F

                chart.data = lineData
                chart.invalidate()
            }
        })
    }

    private fun color(index: Int): Int {
        return when (index) {
            0 -> Color.BLUE
            1 -> Color.RED
            2 -> Color.BLACK
            3 -> Color.rgb(0, 64, 0)
            4 -> Color.YELLOW
            5 -> Color.rgb(255,14,93)
            6 -> Color.rgb(0,255,0)
            7 -> Color.rgb(41,69,225)
            8 -> Color.rgb(233,96, 122)
            9 -> Color.rgb(0, 255,255)
            else -> {
                val rnd = Random()
                Color.rgb(rnd.nextInt(255), rnd.nextInt(255), rnd.nextInt(255))
            }
        }
    }

    class LineChartXAxisValueFormatter : IndexAxisValueFormatter() {
        override fun getFormattedValue(value: Float): String {
            return DateTimeFormatter.ofPattern("dd.MM").format(
                Instant.ofEpochSecond(value.toLong()).atZone(ZoneId.systemDefault())
            )
        }
    }
}