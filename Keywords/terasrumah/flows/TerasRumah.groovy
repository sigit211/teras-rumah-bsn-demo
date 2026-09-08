package terasrumah.flows

import java.nio.file.Files
import java.nio.file.Paths
import java.util.regex.Pattern

import com.kms.katalon.core.exception.StepFailedException
import com.kms.katalon.core.model.FailureHandling
import com.kms.katalon.core.testobject.TestObject
import com.kms.katalon.core.util.KeywordUtil
import com.kms.katalon.core.webui.keyword.WebUiBuiltInKeywords as WebUI
import com.kms.katalon.core.webui.driver.DriverFactory
import org.openqa.selenium.JavascriptExecutor

import core.SafeActionsWeb
import core.StringUtil
import core.TestObjectHelper
import hooks.TestrailIntegration
import internal.GlobalVariable
import terasrumah.constans.UrlConstants
import terasrumah.pages.HomePages
import terasrumah.pages.PropertyDetailPage

class TerasRumah {
	/** Memverifikasi halaman utama dan memastikan tiga submenu Jenis Aset tersedia. */
	static void verifikasiHalamanUtamaDanJenisAset(String screenshotPath) {
		SafeActionsWeb.safeTakeScreenshot("${screenshotPath}/home.png")
		SafeActionsWeb.safeVerifyElementPresentOrFail(HomePages.logoBsn().testObject)
		verifikasiUrl(UrlConstants.HOME_URL)

		SafeActionsWeb.safeClickOrFail(HomePages.jenisAsetMenu().testObject)
		SafeActionsWeb.safeTakeScreenshot("${screenshotPath}/validasi home.png")
		
		TestObject to = new TestObject()
		
		to = HomePages.propertiLelangOption().testObject
		SafeActionsWeb.safeVerifyElementPresentOrFail(to)
		SafeActionsWeb.safeTakeScreenshot("${screenshotPath}/validasi home pic-1.png", to)
		
		to = HomePages.propertiJualSukarelaOption().testObject
		SafeActionsWeb.safeVerifyElementPresentOrFail(to)
		SafeActionsWeb.safeTakeScreenshot("${screenshotPath}/validasi home pic-2.png", to)
		
		to = HomePages.propertiAydaOption().testObject
		SafeActionsWeb.safeVerifyElementPresentOrFail(to)
		SafeActionsWeb.safeTakeScreenshot("${screenshotPath}/validasi home pic-3.png", to)
	}

	/** Memilih submenu listing berdasarkan tipe: lelang, sukarela, atau ayda. */
	static void pilihSubMenu(String listingType, String screenshotPath) {
		String normalizedType = normalizeListingType(listingType)
		TestObject selectedOption = opsiSubMenu(normalizedType)
		if (!SafeActionsWeb.safeVerifyElementPresent(selectedOption, 1, 1)) {
			SafeActionsWeb.safeClickOrFail(HomePages.jenisAsetMenu().testObject)
		}
		switch (normalizedType) {
			case "lelang":
				SafeActionsWeb.safeClickOrFail(HomePages.propertiLelangOption().testObject)
				break
			case "sukarela":
				SafeActionsWeb.safeClickOrFail(HomePages.propertiJualSukarelaOption().testObject)
				break
			case "ayda":
				SafeActionsWeb.safeClickOrFail(HomePages.propertiAydaOption().testObject)
				break
			default:
				throw new IllegalArgumentException("listingType tidak valid: ${normalizedType}")
		}

		WebUI.waitForPageLoad(GlobalVariable.TIMEOUT)
		verifikasiUrl(urlUntukListing(normalizedType))
		SafeActionsWeb.safeVerifyElementPresentOrFail(HomePages.propertyTypeDropdown().testObject)
	}

	/** Memverifikasi tiga pilihan jenis aset yang tersedia pada filter halaman. */
	static void verifikasiFilterJenisAset(String screenshotPath) {
		SafeActionsWeb.safeClickOrFail(HomePages.assetTypeDropdown().testObject)
		SafeActionsWeb.safeTakeScreenshot("${screenshotPath}/validasi filter jenis aset.png")

		TestObject to = HomePages.assetTypeOption("lelang").testObject
		SafeActionsWeb.safeVerifyElementPresentOrFail(to)
		SafeActionsWeb.safeTakeScreenshot("${screenshotPath}/validasi filter jenis aset pic-1.png", to)

		to = HomePages.assetTypeOption("sukarela").testObject
		SafeActionsWeb.safeVerifyElementPresentOrFail(to)
		SafeActionsWeb.safeTakeScreenshot("${screenshotPath}/validasi filter jenis aset pic-2.png", to)

		to = HomePages.assetTypeOption("ayda").testObject
		SafeActionsWeb.safeVerifyElementPresentOrFail(to)
		SafeActionsWeb.safeTakeScreenshot("${screenshotPath}/validasi filter jenis aset pic-3.png", to)
	}

	/** Memilih jenis aset dari filter halaman berdasarkan tipe listing. */
	static void pilihFilterJenisAset(String listingType, String screenshotPath) {
		String normalizedType = normalizeListingType(listingType)
		TestObject selectedOption = HomePages.assetTypeOption(normalizedType).testObject
		if (!SafeActionsWeb.safeVerifyElementPresent(selectedOption, 1, 1)) {
			SafeActionsWeb.safeClickOrFail(HomePages.assetTypeDropdown().testObject)
		}

		SafeActionsWeb.safeClickOrFail(selectedOption)
		WebUI.waitForPageLoad(GlobalVariable.TIMEOUT)
		verifikasiUrl(urlUntukFilterJenisAset(normalizedType))
		SafeActionsWeb.safeVerifyElementPresentOrFail(HomePages.propertyTypeDropdown().testObject)
		SafeActionsWeb.safeTakeScreenshot("${screenshotPath}/filter jenis aset-${normalizedType}.png")
	}

	/** Mengisi filter tipe properti dan rentang harga, lalu menjalankan pencarian. */
	static void pilihFilter(String listingType, String propertyType = "Rumah", String minPrice = "", String maxPrice = "", String searchLocation = "", String screenshotPath) {
		String normalizedType = normalizeListingType(listingType)
		if (searchLocation?.trim()) {
			SafeActionsWeb.safeSetTextOrFail(HomePages.searchLocation().testObject, searchLocation)
		}
		if (propertyType?.trim()) {
			pilihOpsiFilter(HomePages.propertyTypeDropdown().testObject, HomePages.objectListTipeProperti(propertyType).value)
		}
		if (minPrice?.trim()) {
			pilihOpsiFilter(HomePages.minimumPriceDropdown().testObject, HomePages.objectListMin(StringUtil.parseShortValueToLong(minPrice).toString()).value)
		}
		if (maxPrice?.trim()) {
			pilihOpsiFilter(HomePages.maximumPriceDropdown().testObject, HomePages.objectListMax(StringUtil.parseShortValueToLong(maxPrice).toString()).value)
		}

		SafeActionsWeb.safeClickOrFail(HomePages.searchButton().testObject)
		SafeActionsWeb.safeVerifyElementPresentOrFail(HomePages.searchResultsHeading().testObject, GlobalVariable.TIMEOUT)
		verifikasiUrl(urlUntukHasilFilter(normalizedType, propertyType, minPrice, maxPrice, searchLocation))
	}

	/** Memverifikasi hasil pencarian, termasuk kartu properti, empty state, tipe aset, dan technical error. */
	static String verifikasiHasilFilter(String listingType, String propertyType = "Rumah", String minPrice = "", String maxPrice = "", String searchLocation = "", String screenshotPath) {
		String normalizedType = normalizeListingType(listingType)
		verifikasiUrl(urlUntukHasilFilter(normalizedType, propertyType, minPrice, maxPrice, searchLocation))
		SafeActionsWeb.safeVerifyElementPresentOrFail(HomePages.searchResultsHeading().testObject)

		boolean isEmpty = SafeActionsWeb.safeVerifyElementPresent(HomePages.emptyResultState().testObject, 2, 1)
		SafeActionsWeb.safeTakeScreenshot("${screenshotPath}/hasil-${normalizedType}-${propertyType.toLowerCase()}.png")
		//empty state
		if (isEmpty) {
			return null
		}

		TestObject propertyCard = HomePages.propertyCards().testObject
		boolean hasCards = SafeActionsWeb.safeVerifyElementPresent(propertyCard, 2, 1)
		if (!hasCards) {
			throw new StepFailedException("Hasil pencarian tidak menampilkan kartu properti maupun empty state")
		}

		String propertyId = SafeActionsWeb.safeGetAttribute(propertyCard, "data-listing-id", GlobalVariable.TIMEOUT, 1)
		if (!propertyId) {
			throw new StepFailedException("Kartu properti ditemukan, tetapi data-listing-id tidak ditemukan")
		}
		
		if (hasCards && propertyType?.trim()) {
			SafeActionsWeb.safeVerifyElementPresentOrFail(HomePages.propertyCardType(propertyType).testObject)
		}
		
		return propertyId
	}

	/** Membuka kartu properti pertama, memverifikasi halaman detail, lalu screenshot bertahap sampai bagian bawah. */
	static void bukaDetailProperti(String listingType, String propertyId, String screenshotPath) {
		TestObject propertyCard = HomePages.propertyCards().testObject
		SafeActionsWeb.safeVerifyElementPresentOrFail(propertyCard)

		String detailUrl = SafeActionsWeb.safeGetAttribute(propertyCard, "href", GlobalVariable.TIMEOUT, 1)
		if (!detailUrl) {
			throw new StepFailedException("Href detail properti tidak ditemukan")
		}
		if (!detailUrl.contains("id=${propertyId}")) {
			throw new StepFailedException("Href detail tidak sesuai dengan propertyId: ${propertyId}")
		}

		def driver = DriverFactory.getWebDriver()
		Set<String> existingWindowHandles = driver.getWindowHandles() as Set<String>
		((JavascriptExecutor) driver).executeScript("window.open(arguments[0], '_blank');", detailUrl)

		String newWindowHandle = driver.getWindowHandles().find {
			!existingWindowHandles.contains(it)
		}
		if (!newWindowHandle) {
			throw new StepFailedException("Tab baru detail properti tidak terbuka")
		}
		driver.switchTo().window(newWindowHandle)

		WebUI.waitForPageLoad(GlobalVariable.TIMEOUT)
		verifikasiUrlDetailProperti(detailUrl, propertyId)
		SafeActionsWeb.safeVerifyElementPresentOrFail(PropertyDetailPage.assetInformationHeading().testObject)

		String screenshotPrefix = "${screenshotPath}/detail-${normalizeListingType(listingType)}"
		int screenshotIndex = 1
		SafeActionsWeb.safeTakeScreenshot("${screenshotPrefix}-bagian-${String.format('%02d', screenshotIndex)}.png")

		while (true) {
			Map<String, Number> beforeScroll = posisiScroll()
			if (sudahSampaiBawah(beforeScroll)) {
				break
			}

			SafeActionsWeb.safeScrollByOrFail(0, 300, 0, 700)
			WebUI.delay(1)
			Map<String, Number> afterScroll = posisiScroll()
			if (afterScroll.top <= beforeScroll.top) {
				break
			}

			screenshotIndex++
			SafeActionsWeb.safeTakeScreenshot("${screenshotPrefix}-bagian-${String.format('%02d', screenshotIndex)}.png")
		}

		SafeActionsWeb.safeTakeScreenshot("${screenshotPrefix}-paling-bawah.png")
	}

	/** Menjalankan seluruh alur pengujian listing dari halaman utama sampai detail properti. */
	static List<String> endToEnd(String listingType, String propertyType = "Rumah", String minPrice = "", String maxPrice = "", String searchLocation = "", String screenshotPath) {
		List<String> testcaseProperties = new ArrayList<>()
		
		String normalizedType = normalizeListingType(listingType)
		
		//pilih jenis aset dari menu atas
		String screenshotPathVerifhalamanUtama = "${screenshotPath}/verifikasi halaman utama"
		if (!TestrailIntegration.runStep(testcaseProperties, screenshotPathVerifhalamanUtama, "C46", {
			Files.createDirectories(Paths.get(screenshotPathVerifhalamanUtama))
			verifikasiHalamanUtamaDanJenisAset(screenshotPathVerifhalamanUtama)
		})) {
			return testcaseProperties
		}
		
		String screenshotPathPilihSubMenu = "${screenshotPath}/pilih sub menu"
		if (!TestrailIntegration.runStep(testcaseProperties, screenshotPathPilihSubMenu, "C47", {
			Files.createDirectories(Paths.get(screenshotPathPilihSubMenu))
			pilihSubMenu(normalizedType, screenshotPathPilihSubMenu)
		})) {
			return testcaseProperties
		}
		
		//pilih menu aset dari dropdown filter
//		verifikasiFilterJenisAset(screenshotPath)
//		pilihFilterJenisAset(normalizedType, screenshotPath)
		
		String screenshotPathPilihFilter = "${screenshotPath}/pilih filter"
		if (!TestrailIntegration.runStep(testcaseProperties, screenshotPathPilihFilter, "C49", {
			Files.createDirectories(Paths.get(screenshotPathPilihFilter))
			pilihFilter(normalizedType, propertyType, minPrice, maxPrice, searchLocation, screenshotPathPilihFilter)
		})) {
			return testcaseProperties
		}
		
		String propertyId = ""
		String screenshotPathVerifikasiHasilFilter = "${screenshotPath}/verifikasi hasil filter"
		if (!TestrailIntegration.runStep(testcaseProperties, screenshotPathVerifikasiHasilFilter, "C50", {
			Files.createDirectories(Paths.get(screenshotPathVerifikasiHasilFilter))
			propertyId = verifikasiHasilFilter(normalizedType, propertyType, minPrice, maxPrice, searchLocation, screenshotPathVerifikasiHasilFilter)
		})) {
			return testcaseProperties
		}
		
		String screenshotPathBukaDetailProperti = "${screenshotPath}/buka detail properti"
		Files.createDirectories(Paths.get(screenshotPathBukaDetailProperti))
		if (propertyId) {
			if (!TestrailIntegration.runStep(testcaseProperties, screenshotPathBukaDetailProperti, "C51", {
				bukaDetailProperti(normalizedType, propertyId, screenshotPathBukaDetailProperti)
			})) {
				return testcaseProperties
			}
		} else {
			SafeActionsWeb.safeTakeScreenshot("${screenshotPathBukaDetailProperti}/tidak ada data yang ditampilkan.png")
			SafeActionsWeb.safeVerifyElementPresentOrFail(HomePages.emptyResultState().testObject)
		}
		
		
		return testcaseProperties
	}

	//-------------------------------------------------- HELPER --------------------------------------------------\\
	
	/** Membuka dropdown filter dan memilih opsi yang sesuai dengan nilai parameter. */
	private static void pilihOpsiFilter(TestObject dropdown, TestObject option) {
		SafeActionsWeb.safeClickOrFail(dropdown)
		WebUI.scrollToElement(option, 5)
		SafeActionsWeb.safeClickOrFail(option)
	}

	/** Membuka dropdown, menggulir opsi XPath ke viewport, lalu memilihnya. */
	private static void pilihOpsiFilter(TestObject dropdown, String optionXpath) {
		TestObject option = TestObjectHelper.testObject("xpath", optionXpath).testObject
		SafeActionsWeb.safeClickOrFail(dropdown)
		WebUI.scrollToElement(option, 5)
		SafeActionsWeb.safeClickOrFail(option)
	}

	/** Mengambil posisi root scroll dan posisi footer halaman. */
	private static Map<String, Number> posisiScroll() {
		return WebUI.executeJavaScript(
			"""
				const root = document.scrollingElement || document.documentElement;
				const footer = document.querySelector('footer');
				const footerRect = footer ? footer.getBoundingClientRect() : null;
				return {
					top: root.scrollTop,
					viewport: root.clientHeight || window.innerHeight,
					scrollHeight: root.scrollHeight,
					footerBottom: footerRect ? footerRect.bottom : null
				};
			""",
			[]) as Map<String, Number>
	}

	/** Menggunakan footer sebagai patokan utama dan tinggi dokumen sebagai fallback. */
	private static boolean sudahSampaiBawah(Map<String, Number> scrollPosition) {
		Number footerBottom = scrollPosition.footerBottom
		if (footerBottom != null) {
			return footerBottom.doubleValue() <= scrollPosition.viewport.doubleValue() + 2
		}
		return scrollPosition.top.doubleValue() + scrollPosition.viewport.doubleValue() >= scrollPosition.scrollHeight.doubleValue() - 2
	}

	/** Mengembalikan object submenu yang sesuai dengan tipe listing yang dipilih. */
	private static TestObject opsiSubMenu(String listingType) {
		if (listingType == "lelang") {
			return HomePages.propertiLelangOption().testObject
		}
		if (listingType == "sukarela") {
			return HomePages.propertiJualSukarelaOption().testObject
		}
		if (listingType == "ayda") {
			return HomePages.propertiAydaOption().testObject
		}
		throw new IllegalArgumentException("listingType tidak valid: ${listingType}")
	}

	/** Memverifikasi URL aktif terhadap URL yang diharapkan dari UrlConstants. */
	private static void verifikasiUrl(String expectedUrl) {
		String actualUrl = WebUI.getUrl()
		if (!actualUrl.startsWith(expectedUrl)) {
			WebUI.verifyMatch(actualUrl, Pattern.quote(expectedUrl) + ".*", true, FailureHandling.STOP_ON_FAILURE)
		}
	}

	/** Memverifikasi URL detail berdasarkan href kartu dan property ID yang dipilih. */
	private static void verifikasiUrlDetailProperti(String detailUrl, String propertyId) {
		if (!detailUrl.contains("id=${propertyId}")) {
			throw new StepFailedException("Href detail tidak sesuai dengan propertyId: ${propertyId}")
		}

		def typeMatcher = detailUrl =~ /(?:[?&])type=([^&#]+)/
		if (!typeMatcher.find()) {
			throw new StepFailedException("Tipe listing tidak ditemukan pada href detail: ${detailUrl}")
		}

		verifikasiUrl(UrlConstants.propertyDetailUrl(propertyId, typeMatcher.group(1)))
	}

	/** Menormalkan variasi nama listing menjadi nilai parameter URL yang konsisten. */
	private static String normalizeListingType(String listingType) {
		String value = listingType?.trim()?.toLowerCase()
		if (value in ["lelang", "properti lelang"]) return "lelang"
		if (value in ["sukarela", "jual sukarela", "properti jual sukarela"]) return "sukarela"
		if (value in ["ayda", "properti ayda"]) return "ayda"
		throw new IllegalArgumentException("listingType harus lelang, sukarela, atau ayda: ${listingType}")
	}

	/** Mengembalikan path URL halaman listing berdasarkan tipe listing. */
	private static String pathUntukListing(String listingType) {
		if (listingType == "lelang") {
			return "properti-lelang"
		}
		if (listingType == "sukarela") {
			return "properti-sukarela"
		}
		if (listingType == "ayda") {
			return "properti-ayda"
		}
		throw new IllegalArgumentException("listingType tidak valid: ${listingType}")
	}

	/** Mengembalikan URL listing yang didefinisikan di UrlConstants. */
	private static String urlUntukListing(String listingType) {		
		if (listingType == "lelang") {
			return UrlConstants.LELANG_URL
		}
		if (listingType == "sukarela") {
			return UrlConstants.SUKARELA_URL
		}
		if (listingType == "ayda") {
			return UrlConstants.AYDA_URL
		}
		throw new IllegalArgumentException("listingType tidak valid: ${listingType}")
	}

	/** Mengembalikan URL home setelah filter jenis aset dipilih. */
	private static String urlUntukFilterJenisAset(String listingType) {
		"${UrlConstants.BASE_URL}/?listingType=${listingType}"
	}

	/** Memilih format URL hasil filter sesuai halaman listing atau home yang sedang aktif. */
	private static String urlUntukHasilFilter(String listingType, String propertyType, String minPrice, String maxPrice, String searchLocation = "") {
		if (WebUI.getUrl().contains("${UrlConstants.BASE_URL}/?")) {
			return UrlConstants.filteredHomeUrl(listingType, propertyType, minPrice, maxPrice, searchLocation)
		}
		return UrlConstants.filteredUrl(pathUntukListing(listingType), listingType, propertyType, minPrice, maxPrice, searchLocation)
	}
}