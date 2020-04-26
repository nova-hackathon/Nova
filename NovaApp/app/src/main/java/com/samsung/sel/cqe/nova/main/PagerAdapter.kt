package com.samsung.sel.cqe.nova.main

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter

class PagerAdapter(
    fragmentManager: FragmentManager
    , val numOfTabs: Int
) :
    FragmentStatePagerAdapter(fragmentManager, numOfTabs) {
    val tableFragment = TableFragment()
    val novaFragment = NovaFragment(tableFragment)

    override fun getItem(position: Int): Fragment {
        return when (position) {
            0 -> novaFragment
            else -> tableFragment
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