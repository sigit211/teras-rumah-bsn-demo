package terasrumah.pages

import core.TestObjectHelper

public class PropertyDetailPage extends TestObjectHelper {
	// Property detail opened from a result card in a new tab
	static ObjectResult photoButton() {
		testObject("xpath", "//button[normalize-space()='Foto']")
	}

	static ObjectResult videoButton() {
		testObject("xpath", "//button[normalize-space()='Video']")
	}

	static ObjectResult propertyGallery() {
		testObject("xpath", "//figure[.//img[@alt='banner']]")
	}

	static ObjectResult assetLabel(String assetType = "Aset Lelang") {
		testObject("xpath", "//*[normalize-space()='${assetType}']")
	}

	static ObjectResult propertyPrice() {
		testObject("xpath", "//*[contains(normalize-space(), 'Rp') and not(self::button)]")
	}

	static ObjectResult priceTag() {
		testObject("xpath", "//div[contains(@class, 'property-detail')]//*[@class='price']")
	}

	static ObjectResult propertyPriceTag() {
		priceTag()
	}

	static ObjectResult propertyTitle() {
		testObject("xpath", "//h1 | //h2")
	}

	static ObjectResult propertyLocation() {
		testObject("xpath", "//*[contains(@class, 'location')]")
	}

	static ObjectResult propertySummary() {
		testObject("xpath", "//*[contains(normalize-space(), 'Kamar Tidur')]/ancestor::*[1]")
	}

	static ObjectResult assetInformationHeading() {
		testObject("xpath", "//*[self::h1 or self::h2][normalize-space()='Informasi Aset']")
	}

	static ObjectResult assetInformation() {
		testObject("xpath", "//*[normalize-space()='Informasi Aset']/following::*[1]")
	}

	static ObjectResult assetId() {
		testObject("xpath", "//*[normalize-space()='ID Iklan']/following-sibling::*[1]")
	}

	static ObjectResult assetType() {
		testObject("xpath", "//*[normalize-space()='Tipe Aset']/following-sibling::*[1]")
	}

	static ObjectResult whatsappButton() {
		testObject("xpath", "//button[normalize-space()='Whatsapp']")
	}

	static ObjectResult relatedListingsHeading() {
		testObject("xpath", "//*[self::h1 or self::h2][normalize-space()='Listing Lainnya']")
	}

	static ObjectResult relatedListings() {
		testObject("xpath", "//*[normalize-space()='Listing Lainnya']/following::a[contains(@class, 'card-project-link')]")
	}

	static ObjectResult backToListings() {
		testObject("xpath", "//a[normalize-space()='Lihat Semua']")
	}

	static ObjectResult pageFooter() {
		testObject("xpath", "//footer")
	}
}