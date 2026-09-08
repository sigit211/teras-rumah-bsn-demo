package core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.ResolverStyle
import java.time.temporal.ChronoUnit
import java.util.regex.Matcher
import java.util.regex.Pattern

import com.kms.katalon.core.mobile.keyword.MobileBuiltInKeywords as Mobile
import com.kms.katalon.core.testobject.TestObject
import com.kms.katalon.core.util.KeywordUtil

import core.SafeActionsMobile
import core.Util

/**
 * FormatDate
 *
 * Utility untuk manipulasi dan konversi format tanggal/waktu.
 * Class ini tidak bergantung pada Katalon Mobile/WebUI API — murni date logic.
 *
 * Contoh pemakaian:
 * <pre>
 *   // Reformat tanggal dari DB ke CSV
 *   String csv = FormatDate.reformat("2025-09-15", "yyyy-MM-dd", "MM/dd/yyyy")
 *
 *   // Reformat ISO-8601 (auto-detect format)
 *   String tgl = FormatDate.reformat("2026-01-09T13:10:04+07:00", "dd MMMM yyyy")
 *
 *   // Tanggal N hari ke depan dari hari ini (skip weekend)
 *   String jatuhTempo = FormatDate.hitungTanggalKeDepan(5)
 *
 *   // Nama bulan ke angka
 *   int bulan = FormatDate.monthNameToNumber("Februari", "id")  // → 2
 * </pre>
 */
final class FormatDate {

    private FormatDate() {}

    // ── Hitung tanggal ke depan (skip weekend) ────────────────────────────────

    /**
     * Menghitung tanggal N hari ke depan dari <b>hari ini</b>, melewati akhir pekan.
     * Jika hari target jatuh di Sabtu/Minggu, digeser ke Senin berikutnya.
     * @param nHari Jumlah hari ke depan (minimal 1).
     * @return String tanggal dalam format {@code dd/MM/yyyy}.
     * @throws IllegalArgumentException jika {@code nHari < 1}.
     */
    static String hitungTanggalKeDepan(int nHari) {
        if (nHari <= 0) throw new IllegalArgumentException("nHari harus >= 1")
        LocalDate target = LocalDate.now().plusDays(nHari - 1)
        if (target.getDayOfWeek() == DayOfWeek.SATURDAY) target = target.plusDays(2)
        else if (target.getDayOfWeek() == DayOfWeek.SUNDAY) target = target.plusDays(1)
        return target.format(DateTimeFormatter.ofPattern('dd/MM/yyyy'))
    }

    /**
     * Menghitung tanggal N hari ke depan dari <b>tanggal yang ditentukan</b>, melewati akhir pekan.
     * Jika hari target jatuh di Sabtu/Minggu, digeser ke Senin berikutnya.
     * @param startDateStr Tanggal awal dalam format {@code dd/MM/yyyy}.
     * @param nHari        Jumlah hari ke depan (minimal 1).
     * @return String tanggal dalam format {@code dd/MM/yyyy}.
     * @throws IllegalArgumentException jika {@code nHari < 1}.
     */
    static String hitungTanggalKeDepan(String startDateStr, int nHari) {
        if (nHari <= 0) throw new IllegalArgumentException("nHari harus >= 1")
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern('dd/MM/yyyy')
        LocalDate target = LocalDate.parse(startDateStr, fmt).plusDays(nHari - 1)
        if (target.getDayOfWeek() == DayOfWeek.SATURDAY) target = target.plusDays(2)
        else if (target.getDayOfWeek() == DayOfWeek.SUNDAY) target = target.plusDays(1)
        return target.format(fmt)
    }

    // ── Reformat: ISO-8601 auto-detect ────────────────────────────────────────

    /**
     * Reformat string tanggal ISO-8601 ke format output yang diinginkan.
     * Auto-detect format input: {@code OffsetDateTime} → {@code ZonedDateTime} → {@code LocalDateTime}.
     * Contoh input: {@code "2026-01-09T13:10:04+07:00"}.
     * @param dateStr       String tanggal ISO-8601.
     * @param outputPattern Pattern output (contoh: {@code "dd MMMM yyyy"}).
     * @return String tanggal yang sudah diformat.
     */
    static String reformat(String dateStr, String outputPattern) {
        if (!dateStr || !outputPattern) {
            KeywordUtil.markFailedAndStop('[FormatDate.reformat] Parameter tidak boleh kosong')
        }
        DateTimeFormatter outFmt = DateTimeFormatter.ofPattern(outputPattern)
        try {
            return OffsetDateTime.parse(dateStr).format(outFmt)
        } catch (Exception ignored) {}
        try {
            return ZonedDateTime.parse(dateStr).format(outFmt)
        } catch (Exception ignored) {}
        try {
            return LocalDateTime.parse(dateStr).format(outFmt)
        } catch (Exception e) {
            KeywordUtil.markFailed("[FormatDate.reformat] Gagal parse ISO: ${e.message}")
            throw e
        }
    }

    // ── Reformat: explicit input/output pattern + locale ─────────────────────

    /**
     * Reformat string tanggal dari satu format ke format lain dengan dukungan locale.
     * Mendukung input berbahasa Indonesia maupun Inggris (nama bulan, dll).
     *
     * @param dateStr       String tanggal input.
     * @param inputPattern  Pattern format input (contoh: {@code "yyyy-MM-dd"}).
     * @param outputPattern Pattern format output (contoh: {@code "dd MMMM yyyy"}).
     * @param bahasaTarget  Bahasa output: {@code "indonesia"} (default) atau {@code "english"}.
     * @return String tanggal yang sudah diformat.
     */
    static String reformat(String dateStr, String inputPattern, String outputPattern, String bahasaTarget = 'indonesia') {
        if (!dateStr || !inputPattern || !outputPattern) {
            KeywordUtil.markFailedAndStop('[FormatDate.reformat] Parameter tidak boleh kosong')
        }
        try {
            String inPat  = inputPattern.replace('yyyy', 'uuuu')
            String outPat = outputPattern.replace('yyyy', 'uuuu')

            List<Locale> candidateLocales = [new Locale('id', 'ID'), Locale.ENGLISH]
            Locale outLocale = resolveOutputLocale(bahasaTarget)
            DateTimeFormatter outFmt = new DateTimeFormatterBuilder()
                    .parseCaseInsensitive()
                    .appendPattern(outPat)
                    .toFormatter(outLocale)

            boolean hasTime = inPat.matches('.*[HhmsaS].*')

            Exception lastEx = null
            for (Locale loc : candidateLocales) {
                try {
                    DateTimeFormatter inFmt = new DateTimeFormatterBuilder()
                            .parseCaseInsensitive()
                            .appendPattern(inPat)
                            .toFormatter(loc)
                            .withResolverStyle(ResolverStyle.STRICT)
                    if (hasTime) {
                        return LocalDateTime.parse(dateStr, inFmt).format(outFmt)
                    } else {
                        return LocalDate.parse(dateStr, inFmt).format(outFmt)
                    }
                } catch (Exception ex) {
                    lastEx = ex
                }
            }
            KeywordUtil.markFailed("[FormatDate.reformat] Gagal parse dengan inputPattern '${inputPattern}': ${lastEx?.message}")
            throw lastEx
        } catch (Exception e) {
            throw e
        }
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    /**
     * Mengekstrak angka pertama dari sebuah string.
     * Contoh: {@code "Select Year 2025"} → {@code 2025}, {@code "03"} → {@code 3}.
     * @param text String input.
     * @return Angka pertama yang ditemukan, atau {@code -1} jika tidak ada.
     */
    static int extractNumber(String text) {
        Matcher m = Pattern.compile('\\d+').matcher(text ?: '')
        return m.find() ? Integer.parseInt(m.group()) : -1
    }

    /**
     * Mengonversi nama bulan (Indonesia atau Inggris) ke angka 1..12.
     * Menerima nama bulan penuh maupun singkatan (Jan/Januari/January).
     * Jika string mengandung angka 1-12, angka tersebut yang dipakai.
     * @param text String nama bulan.
     * @param lang Bahasa: {@code "en"} untuk Inggris, selain itu dianggap Indonesia.
     * @return Angka bulan 1..12, atau {@code -1} jika tidak dikenali.
     */
    static int monthNameToNumber(String text, String lang) {
        if (!text) return -1
        String lower = text.toLowerCase().trim()

        Matcher m = Pattern.compile('\\b\\d{1,2}\\b').matcher(lower)
        if (m.find()) {
            int n = Integer.parseInt(m.group())
            if (n >= 1 && n <= 12) return n
        }

        boolean isEN = lang?.toLowerCase()?.startsWith('en')
        if (isEN) {
            if (lower.contains('jan')) return 1
            if (lower.contains('feb')) return 2
            if (lower.contains('mar')) return 3
            if (lower.contains('apr')) return 4
            if (lower.contains('may')) return 5
            if (lower.contains('jun')) return 6
            if (lower.contains('jul')) return 7
            if (lower.contains('aug')) return 8
            if (lower.contains('sep')) return 9
            if (lower.contains('oct')) return 10
            if (lower.contains('nov')) return 11
            if (lower.contains('dec')) return 12
        } else {
            if (lower.contains('jan')) return 1
            if (lower.contains('feb')) return 2
            if (lower.contains('mar')) return 3
            if (lower.contains('apr')) return 4
            if (lower.contains('mei') || lower.contains('may')) return 5
            if (lower.contains('jun')) return 6
            if (lower.contains('jul')) return 7
            if (lower.contains('agu') || lower.contains('aug')) return 8
            if (lower.contains('sep')) return 9
            if (lower.contains('okt') || lower.contains('oct')) return 10
            if (lower.contains('nov')) return 11
            if (lower.contains('des') || lower.contains('dec')) return 12
        }
        return -1
    }

    // ── Swipe picker ──────────────────────────────────────────────────────────
	/**
	 * Short-swipe NumberPicker/Spinner berbasis TestObject.
	 * @param to           TestObject kolom (tanggal/bulan/tahun/jam/menit)
	 * @param type         "Date" | "Month" | "Year" | "Hour" | "Minute"
	 * @param targetValue  nilai target (bulan pakai angka 1..12)
	 * @param maxTry       batas swipe
	 */
	public static void swipeOnPicker(TestObject to, String type, int targetValue, int maxTry)
	throws InterruptedException {

		// --- Ambil bahasa device di dalam method (tanpa parameter) ---
		String deviceLanguage = Util.getDeviceLanguage()
		String lang = (deviceLanguage != null && deviceLanguage.trim())
				? deviceLanguage.trim().toLowerCase()
				: "id"
		if (!lang.startsWith("en")) lang = "id"  // normalisasi: selain "en" dianggap "id"

		// --- Ambil rect dari TestObject ---
		int left   = Mobile.getElementLeftPosition(to, 5)
		int top    = Mobile.getElementTopPosition(to, 5)
		int width  = Mobile.getElementWidth(to, 5)
		int height = Mobile.getElementHeight(to, 5)

		int centerX = left + (int)(width / 2)
		int startY  = top + (int)(height * 0.56)   // bawah (short swipe)
		int endY    = top + (int)(height * 0.30)    // atas  (short swipe)

		// --- Dapatkan current value ---
		String currentText = getContentDescOrText(to, 5)
		int currentValue
		if ("Month".equals(type)) {
			currentValue = monthNameToNumber(currentText, lang)
		} else if ("Hour".equals(type) || "Minute".equals(type)) {
			currentValue = safeParseTwoDigitPrefix(currentText)
		} else {
			currentValue = extractNumber(currentText)
		}

		// --- Tentukan arah swipe sekali di awal (langkah minimal) ---
		boolean swipeDown = true
		if ("Year".equals(type)) {
			swipeDown = (targetValue > currentValue)
		} else if ("Month".equals(type)) {
			int forward  = (targetValue - currentValue + 12) % 12
			int backward = (currentValue - targetValue + 12) % 12
			swipeDown = (forward <= backward)
		} else if ("Date".equals(type)) {
			int forward  = (targetValue - currentValue + 31) % 31
			int backward = (currentValue - targetValue + 31) % 31
			swipeDown = (forward <= backward)
		} else if ("Hour".equals(type)) {
			int forward  = (targetValue - currentValue + 24) % 24
			int backward = (currentValue - targetValue + 24) % 24
			swipeDown = (forward <= backward)
		} else if ("Minute".equals(type)) {
			int forward  = (targetValue - currentValue + 60) % 60
			int backward = (currentValue - targetValue + 60) % 60
			swipeDown = (forward <= backward)
		}

		// --- Lakukan short-swipe sampai sesuai ---
		int tries = 0
		while (tries < maxTry) {
			currentText = getContentDescOrText(to, 5)

			if ("Month".equals(type)) {
				currentValue = monthNameToNumber(currentText, lang)
			} else if ("Hour".equals(type) || "Minute".equals(type)) {
				currentValue = safeParseTwoDigitPrefix(currentText)
			} else {
				currentValue = extractNumber(currentText)
			}

			if (currentValue == targetValue) {
				System.out.println(type + " sudah sesuai: " + currentValue)
				return
			}

			String beforeSwipe = currentText

			Mobile.tap(to, 5) // fokuskan kolom
			if (swipeDown) {
				// swipe dari bawah -> atas (umumnya nilai bertambah)
				Mobile.swipe(centerX, startY, centerX, endY)
			} else {
				// swipe dari atas -> bawah
				Mobile.swipe(centerX, endY, centerX, startY)
			}

			// beri waktu animasi settle
			Thread.sleep(400)

			// polling sampai value berubah (anti-baca nilai lama)
			int poll = 0
			while (poll < 10) {
				String afterSwipe = getContentDescOrText(to, 2)
				if (!afterSwipe.equals(beforeSwipe)) {
					break
				}
				Thread.sleep(200)
				poll++
			}

			tries++
		}

		throw new RuntimeException("Gagal menemukan " + type + " = " + targetValue + " setelah " + maxTry + " kali swipe")
	}
	
	private static String getContentDescOrText(TestObject to, int timeout) {
		try {
			String cd = Mobile.getAttribute(to, "contentDescription", timeout)
			if (cd != null && cd.trim().length() > 0 && !"null".equalsIgnoreCase(cd)) {
				return cd.trim()
			}
		} catch (Throwable ignore) {}
		return Mobile.getText(to, timeout).trim()
	}

	/** Ambil "00", "01", … dari prefix text; fallback ke extractNumber */
	private static int safeParseTwoDigitPrefix(String text) {
		if (text == null) return -1
		text = text.trim()
		if (text.length() >= 2 && Character.isDigit(text.charAt(0)) && Character.isDigit(text.charAt(1))) {
			return Integer.parseInt(text.substring(0, 2))
		}
		return extractNumber(text)
	}

    // ── Private helpers ───────────────────────────────────────────────────────

    private static Locale resolveOutputLocale(String bahasaTarget) {
        if (!bahasaTarget?.trim()) return new Locale('id', 'ID')
        switch (bahasaTarget.trim().toLowerCase(Locale.ROOT)) {
            case 'id': case 'in': case 'indo': case 'indonesia':
            case 'bahasa': case 'bahasa indonesia':
                return new Locale('id', 'ID')
            case 'en': case 'eng': case 'english': case 'inggris':
                return Locale.ENGLISH
            default:
                KeywordUtil.logInfo("[FormatDate] bahasaTarget '${bahasaTarget}' tidak dikenal, default ke id-ID.")
                return new Locale('id', 'ID')
        }
    }

	static String tentukanStatusJatuhTempo(String jatuhTempo) {
		if (!jatuhTempo?.trim()) {
			throw new IllegalArgumentException("jatuhTempo tidak boleh kosong")
		}
	
		DateTimeFormatter formatter = new DateTimeFormatterBuilder()
				.appendPattern("uuuu-MM-dd HH:mm:ss")
				.optionalStart()
				.appendFraction(java.time.temporal.ChronoField.NANO_OF_SECOND, 0, 9, true)
				.optionalEnd()
				.toFormatter()
	
		LocalDate tanggalJatuhTempo =
				LocalDateTime.parse(jatuhTempo.trim(), formatter).toLocalDate()
	
		long selisihHari = ChronoUnit.DAYS.between(
				LocalDate.now(),
				tanggalJatuhTempo
		)
	
		if (selisihHari >= 1 && selisihHari <= 7) {
			return "H-${selisihHari}"
		}
	
		if (selisihHari == 0) {
			return "Jatuh Tempo"
		}
	
		if (selisihHari < 0) {
			return "Lewat Jatuh Tempo"
		}
	
		return "Belum Jatuh Tempo"
	}
}
