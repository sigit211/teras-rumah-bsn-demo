package terasrumah.constans

import core.StringUtil
import internal.GlobalVariable

class UrlConstants {
	static final String BASE_URL = GlobalVariable.BASE_URL?.replaceAll('/+$', '')

	// Home and unfiltered listing pages
	static final String HOME_URL = "${BASE_URL}/"
	static final String LELANG_URL = "${BASE_URL}/properti-lelang/?listingType=lelang"
	static final String SUKARELA_URL = "${BASE_URL}/properti-sukarela/?listingType=sukarela"
	static final String AYDA_URL = "${BASE_URL}/properti-ayda/?listingType=ayda"

	// The application updates these query parameters as soon as a filter is selected.
	static String filteredUrl(String listingPath, String listingType, String propertyType = "Rumah", String minPrice = "", String maxPrice = "", String searchLocation = "") {
		String url = "${BASE_URL}/${listingPath}/?listingType=${listingType}"
		if (searchLocation?.trim()) {
			url += "&searchKeyword=${java.net.URLEncoder.encode(searchLocation.trim(), 'UTF-8')}"
		}
		url += "&searchPropertyType=${propertyType}"
		if (minPrice) {
			url += "&searchMinPrice=${StringUtil.parseShortValueToLong(minPrice).toString()}"
		}
		if (maxPrice) {
			url += "&searchMaxPrice=${StringUtil.parseShortValueToLong(maxPrice).toString()}"
		}
		return url
	}

	static String filteredHomeUrl(String listingType, String propertyType = "Rumah", String minPrice = "", String maxPrice = "", String searchLocation = "") {
		String url = "${BASE_URL}/?listingType=${listingType}"
		if (searchLocation?.trim()) {
			url += "&searchKeyword=${java.net.URLEncoder.encode(searchLocation.trim(), 'UTF-8')}"
		}
		url += "&searchPropertyType=${propertyType}"
		if (minPrice) {
			url += "&searchMinPrice=${StringUtil.parseShortValueToLong(minPrice).toString()}"
		}
		if (maxPrice) {
			url += "&searchMaxPrice=${StringUtil.parseShortValueToLong(maxPrice).toString()}"
		}
		return url
	}

	static String propertyDetailUrl(String propertyId, String listingType) {
		"${BASE_URL}/properti/?id=${propertyId}&type=${listingType}"
	}
}