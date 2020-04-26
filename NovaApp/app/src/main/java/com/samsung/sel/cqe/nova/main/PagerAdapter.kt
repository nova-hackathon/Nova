package com.samsung.sel.cqe.nova.main

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter
import com.samsung.sel.cqe.nova.main.map.MapFragment

class PagerAdapter(
    fragmentManager: FragmentManager
    , val numOfTabs: Int, val tabsActivity: TabsActivity
) : FragmentStatePagerAdapter(fragmentManager, numOfTabs) {

    var tableFragment = TableFragment(tabsActivity)
    var mapFragment = MapFragment(tabsActivity)
    var novaFragment = NovaFragment(tabsActivity)

    override fun getItem(position: Int): Fragment {
        return when (position) {
            0 -> {
                novaFragment = NovaFragment(tabsActivity)
                novaFragment
            }
            1 -> {
                tableFragment = TableFragment(tabsActivity)
                tableFragment
            }
            else -> {
                mapFragment = MapFragment(tabsActivity)
                mapFragment
            }
        }
    }


    override fun getPageTitle(position: Int): CharSequence? {
        return when (position) {
            0 -> "Info"
            1 -> "Table"
            else -> "Map"
        }
    }

    override fun getCount(): Int = numOfTabs
}