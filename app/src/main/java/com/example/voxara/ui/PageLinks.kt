package com.example.voxara.ui

/**
 * Ways into a page of the glance pager from outside the app: a tile, a complication, the phone's
 * `voxara://watch/<page>` link. A page name becomes the route [com.example.voxara.ui.screens.HomeScreen]
 * opens on (Now, Dose on Today or Week, More); the old Overview and Headphones names still resolve.
 */
object PageLinks {
    /** The intent extra a tile or complication puts the page name in. */
    const val EXTRA_PAGE = "voxara.page"

    /** The route for a page name (`now`, `today`, `week`, `headphones`, `more`, or a `/path` of the link), or null if it is not one. */
    fun routeFor(page: String?): String? = when (page?.trim('/')?.lowercase()) {
        "home", "overview", "now" -> Routes.NOW
        "today", "dose" -> Routes.TODAY
        "week" -> Routes.WEEK
        "headphones" -> Routes.HEADPHONES
        "more", "menu" -> Routes.MENU
        else -> null
    }

    /** True for a route that opens a page of the pager (as opposed to Ask, Mode, Settings, Calibration). */
    fun isPage(route: String?): Boolean = route == Routes.HOME || routeFor(route) != null
}
