package terasrumah.pages

import core.TestObjectHelper

public class HomePages extends TestObjectHelper{
	// Home page
	static ObjectResult logoBsn() {
		testObject("alt", "logo BSN")
	}

	static ObjectResult homeLink() {
		testObject("xpath", "//a[normalize-space()='Home']")
	}

	static ObjectResult jenisAsetMenu() {
		testObject("xpath", "//a[normalize-space()='Jenis Aset']")
	}

	static ObjectResult searchLocation() {
		testObject("placeholder", "Cari Kota / Lokasi / Developer / Nama Properti")
	}

	static ObjectResult searchButton() {
		testObject("xpath", "//button[normalize-space()='Cari' or .//*[normalize-space()='Cari']]")
	}

	// Jenis Aset dropdown and its three listing pages
	static ObjectResult assetTypeDropdown() {
		testObject("xpath", "//*[contains(@class, 'property-condition-search') or contains(@class, 'dropdown-allproj-condition')]")
	}

	static ObjectResult assetTypeOption(String slug) {
		testObject("xpath", "//ul[@id='search-by-property-condition']/li[@data-slug='${slug}']")
	}

	static ObjectResult propertiLelangOption() {
		testObject("xpath", "//a[normalize-space()='Properti Lelang'] | //li[normalize-space()='Properti Lelang']")
	}

	static ObjectResult propertiJualSukarelaOption() {
		testObject("xpath", "//a[normalize-space()='Properti Jual Sukarela'] | //li[normalize-space()='Properti Jual Sukarela']")
	}

	static ObjectResult propertiAydaOption() {
		testObject("xpath", "//a[normalize-space()='Properti AYDA'] | //li[normalize-space()='Properti AYDA']")
	}

	static ObjectResult pageTitle(String title) {
		testObject("xpath", "//*[self::h1 or self::h2 or self::p][normalize-space()='${title}']")
	}

	// Filter controls, shared by home and all three listing pages
	static ObjectResult propertyTypeDropdown() {
		testObject("xpath", "//*[contains(@class, 'property-type-search')]")
	}

	static ObjectResult propertyTypeOption(String type = "Rumah") {
		testObject("xpath", "//*[self::li or self::option][normalize-space()='${type}']")
	}

	static ObjectResult allPropertyTypesOption() {
		propertyTypeOption("Semua Tipe")
	}

	static ObjectResult rumahOption() {
		propertyTypeOption("Rumah")
	}

	static ObjectResult rukoOption() {
		propertyTypeOption("Ruko")
	}

	static ObjectResult ruangUsahaOption() {
		propertyTypeOption("Ruang Usaha")
	}

	static ObjectResult tanahOption() {
		propertyTypeOption("Tanah")
	}

	static ObjectResult gudangOption() {
		propertyTypeOption("Gudang")
	}

	static ObjectResult apartmentOption() {
		propertyTypeOption("Apartment")
	}

	static ObjectResult pabrikOption() {
		propertyTypeOption("Pabrik")
	}

	static ObjectResult perkantoranOption() {
		propertyTypeOption("Perkantoran")
	}

	static ObjectResult minimumPriceDropdown() {
		testObject("xpath", "//*[contains(@class, 'min-price-search') or contains(@class, 'minimum-price-search')]")
	}

	static ObjectResult maximumPriceDropdown() {
		testObject("xpath", "//*[contains(@class, 'max-price-search') or contains(@class, 'maximum-price-search')]")
	}

	static ObjectResult objectListTipeProperti(String value) {
		testObject("xpath", "//ul[@id='search-by-property-type']/li[text()='${value}']")
	}
	
	static ObjectResult objectListMin(String value) {
		testObject("xpath", "//ul[@id='search-by-min-price']/li[@value='${value}']")
	}

	static ObjectResult objectListMax(String value) {
		testObject("xpath", "//ul[@id='search-by-max-price']/li[@value='${value}']")
	}

	// Filtered home: loading, data, and empty-result states
	static ObjectResult searchResultsHeading() {
		testObject("xpath", "//*[self::h1 or self::h2][normalize-space()='Hasil Pencarian']")
	}

	static ObjectResult loadingState() {
		testObject("xpath", "//*[normalize-space()='loading...']")
	}

	static ObjectResult emptyResultState() {
		testObject("xpath", "//*[normalize-space()='hasil pencarian tidak ditemukan']")
	}

	static ObjectResult propertyCards() {
		testObject("xpath", "//a[contains(@class, 'card-project-link')]")
	}
	
	static ObjectResult propertyCards(String index) {
		testObject("xpath", "(//a[contains(@class, 'card-project-link')])[${index}]")
	}

	static ObjectResult propertyCard(String propertyId) {
		testObject("xpath", "//a[contains(@class, 'card-project-link') and contains(@href, 'id=${propertyId}')]")
	}

	static ObjectResult propertyCardImage() {
		testObject("xpath", "//a[contains(@class, 'card-project-link')]//img[@alt='property-image']")
	}

	static ObjectResult propertyCardTitle() {
		testObject("xpath", "//a[contains(@class, 'card-project-link')]//h3")
	}

	static ObjectResult propertyCardPrice() {
		testObject("xpath", "//a[contains(@class, 'card-project-link')]//*[contains(@class, 'card-price-tag')]")
	}

	static ObjectResult propertyCardPrice(String propertyId) {
		testObject("xpath", "//a[contains(@class, 'card-project-link') and @data-listing-id='${propertyId}']//*[contains(@class, 'card-price-tag')]")
	}

	static ObjectResult propertyCardPriceByHref(String propertyId) {
		testObject("xpath", "//a[contains(@class, 'card-project-link') and contains(@href, 'id=${propertyId}')]//*[contains(@class, 'card-price-tag')]")
	}

	static ObjectResult propertyCardType(String type = "Rumah") {
		testObject("xpath", "//a[contains(@class, 'card-project-link') and .//*[contains(normalize-space(), '${type}')]]")
	}

	static ObjectResult previousPageButton() {
		testObject("xpath", "//*[self::a or self::button][normalize-space()='‹']")
	}

	static ObjectResult nextPageButton() {
		testObject("xpath", "//*[self::a or self::button][normalize-space()='›']")
	}

	static ObjectResult footer() {
		testObject("xpath", "//footer")
	}
}
 