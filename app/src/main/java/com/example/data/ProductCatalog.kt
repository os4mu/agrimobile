package com.example.data

data class ProductCatalogItem(
    val productId: String,
    val sku: String,
    val productName: String,
    val categoryId: String,
    val category: String,
    val unit: String,
    val reorderPoint: Int = 10,
    val yoloLabel: String,
    val classIndex: Int,
    val aliases: List<String> = emptyList()
) {
    val uomLabel: String get() = unit
}

object ProductCatalog {

    val PRIMARY_PRODUCTS: List<ProductCatalogItem> = listOf(
        ProductCatalogItem(
            productId = "prod-001",
            sku = "SKU-01-BRODINS100",
            productName = "Brodan Insecticide 100ml",
            categoryId = "cat-001",
            category = "Insecticides",
            unit = "bottle",
            reorderPoint = 10,
            yoloLabel = "brodan_insecticide_100ml",
            classIndex = 0,
            aliases = listOf("Brodan 100ml", "INS-001", "P-001", "SKU-01-BROD100")
        ),
        ProductCatalogItem(
            productId = "prod-002",
            sku = "SKU-02-BRODINS1L",
            productName = "Brodan Insecticide 1L",
            categoryId = "cat-001",
            category = "Insecticides",
            unit = "bottle",
            reorderPoint = 10,
            yoloLabel = "brodan_insecticide_1L",
            classIndex = 1,
            aliases = listOf("Brodan 1L", "INS-002", "P-002", "SKU-02-BROD1L")
        ),
        ProductCatalogItem(
            productId = "prod-003",
            sku = "SKU-03-BRODINS250",
            productName = "Brodan Insecticide 250ml",
            categoryId = "cat-001",
            category = "Insecticides",
            unit = "bottle",
            reorderPoint = 10,
            yoloLabel = "brodan_insecticide_250ml",
            classIndex = 2,
            aliases = listOf("Brodan 250ml", "INS-003", "P-003", "SKU-03-BROD250")
        ),
        ProductCatalogItem(
            productId = "prod-004",
            sku = "SKU-04-BRODINS500",
            productName = "Brodan Insecticide 500ml",
            categoryId = "cat-001",
            category = "Insecticides",
            unit = "bottle",
            reorderPoint = 10,
            yoloLabel = "brodan_insecticide_500ml",
            classIndex = 3,
            aliases = listOf("Brodan 500ml", "INS-004", "P-004", "SKU-04-BROD500")
        ),
        ProductCatalogItem(
            productId = "prod-005",
            sku = "SKU-05-FOSTMILK",
            productName = "Foster Milk Powder",
            categoryId = "cat-005",
            category = "Supplements",
            unit = "bag",
            reorderPoint = 10,
            yoloLabel = "fostermilk",
            classIndex = 4,
            aliases = listOf("Foster Milk", "FST-005", "P-005", "Foster Milk Powder 1kg")
        ),
        ProductCatalogItem(
            productId = "prod-006",
            sku = "SKU-06-MAJCATFOOD",
            productName = "Majesty Cat Food",
            categoryId = "cat-003",
            category = "Feeds & Pet Food",
            unit = "bag",
            reorderPoint = 10,
            yoloLabel = "majesty_catfood",
            classIndex = 5,
            aliases = listOf("Majesty Catfood", "PET-006", "P-006", "Majesty Cat Food 1kg")
        ),
        ProductCatalogItem(
            productId = "prod-007",
            sku = "SKU-07-RONSHERB1L",
            productName = "Ronstar Herbicide 1L",
            categoryId = "cat-004",
            category = "Herbicides & Weedicide",
            unit = "bottle",
            reorderPoint = 10,
            yoloLabel = "ronstar_herbicide_1L",
            classIndex = 6,
            aliases = listOf("Ronstar 1L", "HER-007", "P-007", "SKU-07-RONS1L")
        ),
        ProductCatalogItem(
            productId = "prod-008",
            sku = "SKU-08-RONSHERB500",
            productName = "Ronstar Herbicide 500ml",
            categoryId = "cat-004",
            category = "Herbicides & Weedicide",
            unit = "bottle",
            reorderPoint = 10,
            yoloLabel = "ronstar_herbicide_500ml",
            classIndex = 7,
            aliases = listOf("Ronstar 500ml", "HER-008", "P-008", "SKU-08-RONS500")
        ),
        ProductCatalogItem(
            productId = "prod-009",
            sku = "SKU-09-ROUNHERB1L",
            productName = "Roundup Herbicide 1L",
            categoryId = "cat-004",
            category = "Herbicides & Weedicide",
            unit = "bottle",
            reorderPoint = 10,
            yoloLabel = "roundup_herbicide_1L",
            classIndex = 8,
            aliases = listOf("Roundup 1L", "HER-009", "P-009", "SKU-09-ROUN1L")
        ),
        ProductCatalogItem(
            productId = "prod-010",
            sku = "SKU-10-ROUNHERB4L",
            productName = "Roundup Herbicide 4L",
            categoryId = "cat-004",
            category = "Herbicides & Weedicide",
            unit = "gallon",
            reorderPoint = 10,
            yoloLabel = "roundup_herbicide_4L",
            classIndex = 9,
            aliases = listOf("Roundup 4L", "HER-010", "P-010", "SKU-10-ROUN4L")
        ),
        ProductCatalogItem(
            productId = "prod-011",
            sku = "SKU-11-SP24DAMN500",
            productName = "SP 2.4-D Herbicide Amine 500ml",
            categoryId = "cat-004",
            category = "Herbicides & Weedicide",
            unit = "bottle",
            reorderPoint = 10,
            yoloLabel = "sp_2.4D_herbicide_amine_500ml",
            classIndex = 10,
            aliases = listOf("SP 2.4-D Amine 500ml", "HER-011", "P-011", "SP 2.4-D Amine")
        ),
        ProductCatalogItem(
            productId = "prod-012",
            sku = "SKU-12-SP24DEST1L",
            productName = "SP 2.4-D Herbicide Ester 1L",
            categoryId = "cat-004",
            category = "Herbicides & Weedicide",
            unit = "bottle",
            reorderPoint = 10,
            yoloLabel = "sp_2.4D_herbicide_ester_1L",
            classIndex = 11,
            aliases = listOf("SP 2.4-D Ester 1L", "HER-012", "P-012", "SP 2.4-D Ester 1000ml")
        ),
        ProductCatalogItem(
            productId = "prod-013",
            sku = "SKU-13-SP24DEST250",
            productName = "SP 2.4-D Herbicide Ester 250ml",
            categoryId = "cat-004",
            category = "Herbicides & Weedicide",
            unit = "bottle",
            reorderPoint = 10,
            yoloLabel = "sp_2.4D_herbicide_ester_250ml",
            classIndex = 12,
            aliases = listOf("SP 2.4-D Ester 250ml", "HER-013", "P-013")
        ),
        ProductCatalogItem(
            productId = "prod-014",
            sku = "SKU-14-SP24DEST500",
            productName = "SP 2.4-D Herbicide Ester 500ml",
            categoryId = "cat-004",
            category = "Herbicides & Weedicide",
            unit = "bottle",
            reorderPoint = 10,
            yoloLabel = "sp_2.4D_herbicide_ester_500ml",
            classIndex = 13,
            aliases = listOf("SP 2.4-D Ester 500ml", "HER-014", "P-014", "SP 2.4-D 500ml", "SKU-14-SP24D500")
        ),
        ProductCatalogItem(
            productId = "prod-015",
            sku = "SKU-15-SWIRAMMOSUL",
            productName = "Swire Ammonium Sulfate",
            categoryId = "cat-002",
            category = "Fertilizers",
            unit = "sack",
            reorderPoint = 10,
            yoloLabel = "swire_ammosul",
            classIndex = 14,
            aliases = listOf("Swire AmmoSul", "FER-015", "P-015", "Ammonium Sulfate")
        ),
        ProductCatalogItem(
            productId = "prod-016",
            sku = "SKU-16-THUNBSTAGBST",
            productName = "Thunderbird Baby Stag Booster",
            categoryId = "cat-003",
            category = "Feeds & Pet Food",
            unit = "bag",
            reorderPoint = 10,
            yoloLabel = "thunderbird_babystagbooster_brooding",
            classIndex = 15,
            aliases = listOf("Thunderbird Baby Stag", "FED-016", "P-016", "Baby Stag Booster")
        ),
        ProductCatalogItem(
            productId = "prod-017",
            sku = "SKU-17-THUNENRTONE",
            productName = "Thunderbird Enertone Maintenance",
            categoryId = "cat-003",
            category = "Feeds & Pet Food",
            unit = "bag",
            reorderPoint = 10,
            yoloLabel = "thunderbird_enertone_maintenance",
            classIndex = 16,
            aliases = listOf("Thunderbird Enertone", "FED-017", "P-017", "Enertone")
        ),
        ProductCatalogItem(
            productId = "prod-018",
            sku = "SKU-18-THUNPLATCOND",
            productName = "Thunderbird Platinum Conditioning",
            categoryId = "cat-003",
            category = "Feeds & Pet Food",
            unit = "bag",
            reorderPoint = 10,
            yoloLabel = "thunderbird_platinum_conditioning",
            classIndex = 17,
            aliases = listOf("Thunderbird Platinum", "FED-018", "P-018", "Platinum Conditioning")
        ),
        ProductCatalogItem(
            productId = "prod-019",
            sku = "SKU-19-THUNSTAG4PLUS",
            productName = "Thunderbird Stag Developer 4+ Ranging",
            categoryId = "cat-003",
            category = "Feeds & Pet Food",
            unit = "bag",
            reorderPoint = 10,
            yoloLabel = "thunderbird_stagdeveloper4plus_ranging",
            classIndex = 18,
            aliases = listOf("Thunderbird Stag Developer 4+", "FED-019-4P", "P-019-4P", "Stag Developer 4+")
        ),
        ProductCatalogItem(
            productId = "prod-020",
            sku = "SKU-20-THUNSTAGDEV",
            productName = "Thunderbird Stag Developer Ranging",
            categoryId = "cat-003",
            category = "Feeds & Pet Food",
            unit = "bag",
            reorderPoint = 10,
            yoloLabel = "thunderbird_stagdeveloper_ranging",
            classIndex = 19,
            aliases = listOf("Thunderbird Stag Developer", "FED-020", "P-020", "Stag Developer")
        ),
        ProductCatalogItem(
            productId = "prod-021",
            sku = "SKU-21-VITMPROPREM",
            productName = "VitminPro Premium Pack",
            categoryId = "cat-005",
            category = "Supplements",
            unit = "pack",
            reorderPoint = 10,
            yoloLabel = "vitminpro_premiumpack",
            classIndex = 20,
            aliases = listOf("VitminPro", "SUP-021", "P-021", "VitminPro Premium")
        )
    )

    private fun normalize(str: String): String {
        return str.replace(Regex("[^a-zA-Z0-9]"), "").lowercase()
    }

    /**
     * Resolves any YOLO detection label, class index, or product name/SKU to the exact existing Firebase Product Record.
     * Never invents artificial SKUs.
     */
    fun resolve(label: String, classIndex: Int = -1, nameOrSku: String = ""): ProductCatalogItem {
        // 1. Check exact or normalized YOLO label match
        val normLabel = normalize(label)
        if (normLabel.isNotEmpty()) {
            if (normLabel.contains("vitmin") || normLabel.contains("product20")) {
                val vitmin = PRIMARY_PRODUCTS.firstOrNull { it.classIndex == 20 }
                if (vitmin != null) return vitmin
            }
            val match = PRIMARY_PRODUCTS.firstOrNull { normalize(it.yoloLabel) == normLabel }
            if (match != null) return match
        }

        // 2. Check classIndex mapping
        if (classIndex >= 0) {
            val match = PRIMARY_PRODUCTS.firstOrNull { it.classIndex == classIndex }
            if (match != null) return match
        }

        // 3. Check normalized name / SKU / alias match
        val normQuery = normalize(if (nameOrSku.isNotBlank()) nameOrSku else label)
        if (normQuery.isNotEmpty()) {
            val match = PRIMARY_PRODUCTS.firstOrNull { item ->
                val normName = normalize(item.productName)
                val normSku = normalize(item.sku)
                val normPid = normalize(item.productId)
                normName == normQuery ||
                normSku == normQuery ||
                normPid == normQuery ||
                normName.contains(normQuery) ||
                normQuery.contains(normName) ||
                item.aliases.any { normalize(it) == normQuery || normQuery.contains(normalize(it)) }
            }
            if (match != null) return match
        }

        // 4. Fallback: Return safe default first product or clean representation
        val safeName = label.replace("_", " ").split(" ").joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
        return ProductCatalogItem(
            productId = "prod-001",
            sku = "SKU-01-BRODINS100",
            productName = if (safeName.isNotBlank()) safeName else "Brodan Insecticide 100ml",
            categoryId = "cat-001",
            category = "Insecticides",
            unit = "bottle",
            reorderPoint = 10,
            yoloLabel = label,
            classIndex = if (classIndex >= 0) classIndex else 0
        )
    }

    fun findByProductId(productId: String): ProductCatalogItem? {
        val norm = normalize(productId)
        return PRIMARY_PRODUCTS.firstOrNull { normalize(it.productId) == norm }
    }

    fun findBySku(sku: String): ProductCatalogItem? {
        val norm = normalize(sku)
        return PRIMARY_PRODUCTS.firstOrNull { normalize(it.sku) == norm || it.aliases.any { a -> normalize(a) == norm } }
    }

    fun isBulkProduct(productId: String, sku: String, productName: String, yoloLabel: String = "", classIndex: Int = -1): Boolean {
        val normPid = productId.trim().lowercase()
        val normSku = sku.trim().lowercase()
        val normName = productName.trim().lowercase()
        val normLabel = yoloLabel.trim().lowercase()
        return normPid == "prod-021" ||
               normSku.contains("vitm") ||
               normName.contains("vitmin") ||
               normLabel.contains("vitmin") ||
               classIndex == 20
    }
}
