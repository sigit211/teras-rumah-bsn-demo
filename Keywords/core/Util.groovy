package core

import com.kms.katalon.core.annotation.Keyword
import com.kms.katalon.core.mobile.keyword.MobileBuiltInKeywords as Mobile
import static com.kms.katalon.core.testcase.TestCaseFactory.findTestCase
import com.kms.katalon.core.mobile.keyword.internal.MobileDriverFactory
import com.kms.katalon.core.testobject.ConditionType
import com.kms.katalon.core.testobject.TestObject
import com.kms.katalon.core.util.KeywordUtil
import com.kms.katalon.core.webui.driver.DriverFactory
import com.kms.katalon.util.CryptoUtil

import internal.GlobalVariable

import io.appium.java_client.AppiumBy
import io.appium.java_client.AppiumDriver
import io.appium.java_client.android.AndroidDriver

import org.openqa.selenium.By
import org.openqa.selenium.JavascriptExecutor
import org.openqa.selenium.WebElement

import core.SafeActionsMobile
import core.TestObjectHelper.ObjectResult

class Util {

	//============================================= DATA =============================================//
	/**
	 * decrypt string
	 */
	static String decryptKatalon(String enc) {
		if (enc == null) return null
		try {
			return CryptoUtil.decode(CryptoUtil.getDefault(enc))
		} catch (Exception e) {
			KeywordUtil.markWarning("Gagal dekripsi: " + e.getMessage())
			return enc
		}
	}

	//============================================= COMPARISON METHOD =============================================//
	/**
	 * Helper untuk mengecek apakah string null atau kosong (blank).
	 */
	static boolean isNullOrEmpty(String s) {
		return s == null || s.trim().isEmpty()
	}

	/**
	 * Helper untuk membandingkan string secara case-insensitive dan aman terhadap null.
	 */
	private static boolean safeEqualsIgnoreCase(String a, String b) {
		if (a == null || b == null) return false
		return a.equalsIgnoreCase(b)
	}

	//============================================= SCROLL =============================================//
	/**
	 * Scroll ke elemen berdasarkan resource-id (dan opsional teks).
	 */
	static void scrollToObjectByLocatorResourceId(String resId, String textContent = "", boolean isExactMatch = false) {
		KeywordUtil.logInfo("[Util.scrollToObjectByLocatorResourceId] START resId=${resId} textContent=${textContent} isExactMatch=${isExactMatch}")
		AppiumDriver driver = MobileDriverFactory.getDriver()

		// 1. Base selector (resource-id)
		String selector = "new UiSelector().resourceId(\"${resId}\")"

		// 2. Cek apakah ada teks yang ingin difilter
		if (textContent != null && !textContent.trim().isEmpty()) {
			if (isExactMatch) {
				// Jika isExactMatch diset true, cari yang sama persis
				selector += ".text(\"${textContent}\")"
			} else {
				// Jika isExactMatch false (bawaan), cari pakai contains
				selector += ".textContains(\"${textContent}\")"
			}
		}

		// 3. Rangkai kode UIAutomator
		String uiAutomatorCode = "new UiScrollable(new UiSelector().scrollable(true)).scrollIntoView(" + selector + ")"
		KeywordUtil.logInfo("[Util.scrollToObjectByLocatorResourceId] uiAutomator=${uiAutomatorCode}")

		// 4. Eksekusi
		driver.findElement(AppiumBy.androidUIAutomator(uiAutomatorCode))
		KeywordUtil.logInfo("[Util.scrollToObjectByLocatorResourceId] END")
	}

	/**
	 * Swipe layar ke arah tertentu menggunakan koordinat Y relatif terhadap tinggi layar.
	 * Arah scroll ditentukan oleh nilai startYRatio vs endYRatio:
	 *   startYRatio > endYRatio → scroll ke atas (konten naik, elemen di bawah tampil)
	 *   startYRatio < endYRatio → scroll ke bawah (konten turun, elemen di atas tampil)
	 * Dimensi layar diambil secara otomatis — pemanggil tidak perlu meneruskan deviceWidth/deviceHeight.
	 * @param startYRatio Rasio Y mulai swipe dari tinggi layar (default: 0.8)
	 * @param endYRatio   Rasio Y akhir swipe dari tinggi layar (default: 0.7)
	 */
	static void swipeScroll(double startYRatio = 0.8, double endYRatio = 0.7) {
		KeywordUtil.logInfo("[Util.swipeScroll] START startYRatio=${startYRatio} endYRatio=${endYRatio}")
		int deviceHeight = SafeActionsMobile.safeGetDeviceHeight()
		int deviceWidth  = (int) Math.round(SafeActionsMobile.safeGetDeviceWidth() * 0.08)
		SafeActionsMobile.safeSwipe(deviceWidth, (int) Math.round(deviceHeight * startYRatio), deviceWidth, (int) Math.round(deviceHeight * endYRatio))
		KeywordUtil.logInfo("[Util.swipeScroll] END")
	}

	//============================================= GET OBJECT IN SCREEN =============================================//
	/**
	 * Mencetak semua elemen yang tampil di layar ke console.
	 */
	static void printAllScreen() {
		KeywordUtil.logInfo("[Util.printAllScreen] START")
		String pageSource = MobileDriverFactory.getDriver().getPageSource()
		def xml = new XmlSlurper().parseText(pageSource)

		xml."**".each { node ->
			println "CLASS       : ${node.name()}"
			println "TEXT        : ${node.@text}"
			println "RESOURCE-ID : ${node.@'resource-id'}"
			println "CONTENT-DESC: ${node.@'content-desc'}"
			println "-------------------------------------"
		}
	}

	/**
	 * Mencetak semua elemen yang tampil di layar ke file txt di folder screenshotPath.
	 * File disimpan di: GlobalVariable.SCREENSHOT_PATH + screenshotPath + /screen_dump_<timestamp>.txt
	 */
	static void printAllScreenToFile(String screenshotPath) {
		KeywordUtil.logInfo("[Util.printAllScreenToFile] START screenshotPath=${screenshotPath}")
		String pageSource = MobileDriverFactory.getDriver().getPageSource()
		def xml = new XmlSlurper().parseText(pageSource)

		StringBuilder sb = new StringBuilder()
		xml."**".each { node ->
			sb.append("CLASS       : ${node.name()}\n")
			sb.append("TEXT        : ${node.@text}\n")
			sb.append("RESOURCE-ID : ${node.@'resource-id'}\n")
			sb.append("CONTENT-DESC: ${node.@'content-desc'}\n")
			sb.append("-------------------------------------\n")
		}

		String dir = GlobalVariable.SCREENSHOT_PATH + screenshotPath
		new File(dir).mkdirs()
		String timestamp = new Date().format("yyyyMMdd_HHmmss")
		String filePath = "${dir}/screen_dump_${timestamp}.txt"
		new File(filePath).text = sb.toString()

		KeywordUtil.logInfo("[Util.printAllScreenToFile] END → disimpan ke: ${filePath}")
	}

	//============================================= TABLE =============================================//
	/**
	 * Menghapus semua baris pada tabel dengan mengetuk tombol hapus satu per satu.
	 *
	 * Jumlah iterasi ditentukan dari jumlah elemen yang ditemukan via {@code xpathRows}.
	 * Setiap iterasi selalu mengetuk posisi {@code "1"} karena setelah
	 * sebuah baris dihapus, baris berikutnya bergeser naik ke posisi tersebut.
	 *
	 * Contoh penggunaan:
	 * <pre>
	 *   // Tanpa afterHapus
	 *   Util.hapusSemuaIsiTabel(
	 *       HalamanDataPendapatanSurveiWawancara.tableDaftarPendapatanLainnya().value,
	 *       { String idx -> HalamanDataPendapatanSurveiWawancara.textViewHapusTabel(idx).testObject }
	 *   )
	 *
	 *   // Dengan afterHapus — misal delay setelah tiap hapus
	 *   Util.hapusSemuaIsiTabel(
	 *       HalamanDataPendapatanSurveiWawancara.tableDaftarPendapatanLainnya().value,
	 *       { String idx -> HalamanDataPendapatanSurveiWawancara.textViewHapusTabel(idx).testObject },
	 *       { Mobile.delay(0.5) }
	 *   )
	 *
	 *   // Dengan afterHapus — misal konfirmasi dialog setelah tiap hapus
	 *   Util.hapusSemuaIsiTabel(
	 *       HalamanDataPendapatanSurveiWawancara.tableDaftarPendapatanLainnya().value,
	 *       { String idx -> HalamanDataPendapatanSurveiWawancara.textViewHapusTabel(idx).testObject },
	 *       { SafeActionsMobile.safeTapOrFail(HalamanKonfirmasi.btnYa().testObject) }
	 *   )
	 * </pre>
	 *
	 * @param xpathRows      XPath untuk menghitung jumlah baris yang ada di tabel.
	 * @param getHapusObject Closure yang menerima index (String) dan mengembalikan TestObject tombol hapus.
	 * @param afterHapus     Closure opsional yang dipanggil setelah setiap penghapusan (boleh {@code null}).
	 *                       Contoh: {@code { Mobile.delay(0.5) }} atau {@code { Util.handlePermissions() }}
	 */
	@Keyword
	static void hapusSemuaIsiTabel(String xpathRows, Closure<TestObject> getHapusObject, Closure afterHapus = null) {
		KeywordUtil.logInfo("[Util.hapusSemuaIsiTabel] START xpathRows=${xpathRows}")
		String posisiHapus = "1"

		AppiumDriver driver = MobileDriverFactory.getDriver()
		List<WebElement> elements = driver.findElements(By.xpath(xpathRows))
		int jumlah = elements.size()
		KeywordUtil.logInfo("[Util.hapusSemuaIsiTabel] Jumlah baris yang akan dihapus: ${jumlah}")

		for (int i = 1; i <= jumlah; i++) {
			Mobile.delay(0.5)
			TestObject hapusObject = getHapusObject(posisiHapus)
			KeywordUtil.logInfo("[Util.hapusSemuaIsiTabel] Hapus iterasi ke-${i} pada posisi ${posisiHapus}")
			SafeActionsMobile.safeTapOrFail(hapusObject)
			afterHapus?.call()
		}

		KeywordUtil.logInfo("[Util.hapusSemuaIsiTabel] END → ${jumlah} baris berhasil dihapus")
	}

	//============================================= HIGHLIGHT TEST OBJECT (WEB) =============================================//
	static void highlightElement(WebElement element) {
		String borderColor = GlobalVariable.BORDER_COLOR
		JavascriptExecutor js = (JavascriptExecutor) DriverFactory.getWebDriver()

		js.executeScript("""
			arguments[0].style.border='4px solid ${borderColor}';
	    """, element)
	}

	static void removeHighlight(WebElement element) {
		JavascriptExecutor js = (JavascriptExecutor) DriverFactory.getWebDriver()

		js.executeScript("""
	        arguments[0].style.border='';
	    """, element)
	}
}