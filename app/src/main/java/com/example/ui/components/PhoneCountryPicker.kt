package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.ui.theme.EmeraldVerified
import com.example.ui.theme.GoldMetallic

data class CountryDialCode(
    val iso: String,
    val flag: String,
    val nameAr: String,
    val dialCode: String,
    val sampleLocal: String,
    val maskGroups: List<Int> = listOf(3, 3, 4),
    val minLocalDigits: Int = 7,
    val maxLocalDigits: Int = 10
) {
    val maskPattern: String
        get() = maskGroups.joinToString(" ") { "X".repeat(it) }
}

data class MaskedPhoneResult(
    val formatted: String,
    val origToTrans: IntArray,
    val transToOrig: IntArray
)

class PhoneMaskVisualTransformation(
    private val maskGroups: List<Int>
) : androidx.compose.ui.text.input.VisualTransformation {

    override fun filter(text: androidx.compose.ui.text.AnnotatedString): androidx.compose.ui.text.input.TransformedText {
        val raw = text.text
        if (raw.isEmpty() || raw.startsWith("+") || raw.startsWith("00")) {
            return androidx.compose.ui.text.input.TransformedText(
                text,
                androidx.compose.ui.text.input.OffsetMapping.Identity
            )
        }
        val masked = PhoneNumberHelper.applyMaskWithMapping(raw, maskGroups)
        val offsetMapping = object : androidx.compose.ui.text.input.OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                val clamped = offset.coerceIn(0, masked.origToTrans.lastIndex)
                return masked.origToTrans[clamped]
            }

            override fun transformedToOriginal(offset: Int): Int {
                val clamped = offset.coerceIn(0, masked.transToOrig.lastIndex)
                return masked.transToOrig[clamped]
            }
        }
        return androidx.compose.ui.text.input.TransformedText(
            androidx.compose.ui.text.AnnotatedString(masked.formatted),
            offsetMapping
        )
    }
}

object PhoneNumberHelper {

    val countries: List<CountryDialCode> = listOf(
        CountryDialCode("SA", "\uD83C\uDDF8\uD83C\uDDE6", "السعودية", "+966", "501234567", listOf(2, 3, 4), 9, 9),
        CountryDialCode("YE", "\uD83C\uDDFE\uD83C\uDDEA", "اليمن", "+967", "773429852", listOf(3, 3, 3), 9, 9),
        CountryDialCode("AE", "\uD83C\uDDE6\uD83C\uDDEA", "الإمارات", "+971", "501234567", listOf(2, 3, 4), 9, 9),
        CountryDialCode("EG", "\uD83C\uDDEA\uD83C\uDDEC", "مصر", "+20", "1012345678", listOf(3, 3, 4), 10, 10),
        CountryDialCode("KW", "\uD83C\uDDF0\uD83C\uDDFC", "الكويت", "+965", "50123456", listOf(4, 4), 8, 8),
        CountryDialCode("QA", "\uD83C\uDDF6\uD83C\uDDE6", "قطر", "+974", "33123456", listOf(4, 4), 8, 8),
        CountryDialCode("BH", "\uD83C\uDDE7\uD83C\uDDED", "البحرين", "+973", "33123456", listOf(4, 4), 8, 8),
        CountryDialCode("OM", "\uD83C\uDDF4\uD83C\uDDF2", "عُمان", "+968", "91234567", listOf(4, 4), 8, 8),
        CountryDialCode("JO", "\uD83C\uDDEF\uD83C\uDDF4", "الأردن", "+962", "791234567", listOf(2, 3, 4), 9, 9),
        CountryDialCode("IQ", "\uD83C\uDDEE\uD83C\uDDF6", "العراق", "+964", "7701234567", listOf(3, 3, 4), 10, 10),
        CountryDialCode("SY", "\uD83C\uDDF8\uD83C\uDDFE", "سوريا", "+963", "931234567", listOf(3, 3, 3), 9, 9),
        CountryDialCode("LB", "\uD83C\uDDF1\uD83C\uDDE7", "لبنان", "+961", "71123456", listOf(2, 3, 3), 7, 8),
        CountryDialCode("PS", "\uD83C\uDDF5\uD83C\uDDF8", "فلسطين", "+970", "591234567", listOf(3, 3, 3), 9, 9),
        CountryDialCode("SD", "\uD83C\uDDF8\uD83C\uDDE9", "السودان", "+249", "912345678", listOf(2, 3, 4), 9, 9),
        CountryDialCode("MA", "\uD83C\uDDF2\uD83C\uDDE6", "المغرب", "+212", "612345678", listOf(3, 2, 2, 2), 9, 9),
        CountryDialCode("DZ", "\uD83C\uDDE9\uD83C\uDDFF", "الجزائر", "+213", "551234567", listOf(3, 2, 2, 2), 9, 9),
        CountryDialCode("TN", "\uD83C\uDDF9\uD83C\uDDF3", "تونس", "+216", "20123456", listOf(2, 3, 3), 8, 8),
        CountryDialCode("LY", "\uD83C\uDDF1\uD83C\uDDFE", "ليبيا", "+218", "912345678", listOf(2, 3, 4), 9, 9),
        CountryDialCode("MR", "\uD83C\uDDF2\uD83C\uDDF7", "موريتانيا", "+222", "22123456", listOf(2, 2, 2, 2), 8, 8),
        CountryDialCode("SO", "\uD83C\uDDF8\uD83C\uDDF4", "الصومال", "+252", "612345678", listOf(2, 3, 4), 8, 9),
        CountryDialCode("DJ", "\uD83C\uDDE9\uD83C\uDDEF", "جيبوتي", "+253", "77123456", listOf(2, 2, 2, 2), 8, 8),
        CountryDialCode("KM", "\uD83C\uDDF0\uD83C\uDDF2", "جزر القمر", "+269", "3212345", listOf(3, 2, 2), 7, 7),
        CountryDialCode("TR", "\uD83C\uDDF9\uD83C\uDDF7", "تركيا", "+90", "5012345678", listOf(3, 3, 2, 2), 10, 10),
        CountryDialCode("GB", "\uD83C\uDDEC\uD83C\uDDE7", "بريطانيا", "+44", "7700900123", listOf(4, 6), 10, 10),
        CountryDialCode("US", "\uD83C\uDDFA\uD83C\uDDF8", "أمريكا / كندا", "+1", "2025550123", listOf(3, 3, 4), 10, 10),
        CountryDialCode("DE", "\uD83C\uDDE9\uD83C\uDDEA", "ألمانيا", "+49", "15123456789", listOf(3, 4, 4), 10, 11),
        CountryDialCode("FR", "\uD83C\uDDEB\uD83C\uDDF7", "فرنسا", "+33", "612345678", listOf(1, 2, 2, 2, 2), 9, 9),
        CountryDialCode("IN", "\uD83C\uDDEE\uD83C\uDDF3", "الهند", "+91", "9876543210", listOf(5, 5), 10, 10),
        CountryDialCode("PK", "\uD83C\uDDF5\uD83C\uDDF0", "باكستان", "+92", "3001234567", listOf(3, 7), 10, 10),
        CountryDialCode("MY", "\uD83C\uDDF2\uD83C\uDDFE", "ماليزيا", "+60", "123456789", listOf(2, 3, 4), 9, 10),
        CountryDialCode("ID", "\uD83C\uDDEE\uD83C\uDDE9", "إندونيسيا", "+62", "8123456789", listOf(3, 4, 4), 9, 12)
    )

    val quickCountries: List<CountryDialCode> = listOf(
        countries[0], // SA +966
        countries[1], // YE +967
        countries[2], // AE +971
        countries[3], // EG +20
        countries[4]  // KW +965
    )

    val defaultCountry: CountryDialCode = countries.first()

    private val countriesByCodeLengthDesc: List<CountryDialCode> =
        countries.sortedByDescending { it.dialCode.length }

    fun toAsciiDigits(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            when (ch) {
                in '٠'..'٩' -> sb.append(('0'.code + (ch.code - '٠'.code)).toChar())
                in '۰'..'۹' -> sb.append(('0'.code + (ch.code - '۰'.code)).toChar())
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun cleanInput(raw: String): String {
        val ascii = toAsciiDigits(raw)
        val sb = StringBuilder(ascii.length)
        ascii.forEachIndexed { index, ch ->
            if (ch in '0'..'9' || (ch == '+' && index == 0)) {
                sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun applyMaskWithMapping(raw: String, maskGroups: List<Int>): MaskedPhoneResult {
        val out = StringBuilder(raw.length + maskGroups.size)
        val origToTrans = IntArray(raw.length + 1)
        val transToOrigList = ArrayList<Int>(raw.length + maskGroups.size + 1)

        var groupIdx = 0
        var inGroupCount = 0
        var currentGroupSize = maskGroups.firstOrNull()?.coerceAtLeast(1) ?: 3

        for (i in raw.indices) {
            if (inGroupCount == currentGroupSize) {
                out.append(' ')
                transToOrigList.add(i)
                groupIdx++
                inGroupCount = 0
                currentGroupSize = maskGroups.getOrNull(groupIdx)?.coerceAtLeast(1) ?: 4
            }
            origToTrans[i] = out.length
            out.append(raw[i])
            transToOrigList.add(i)
            inGroupCount++
        }
        origToTrans[raw.length] = out.length
        transToOrigList.add(raw.length)

        return MaskedPhoneResult(
            formatted = out.toString(),
            origToTrans = origToTrans,
            transToOrig = transToOrigList.toIntArray()
        )
    }

    fun formatLocalWithMask(rawLocal: String, maskGroups: List<Int>): String {
        val digits = cleanInput(rawLocal).trimStart('+')
        if (digits.isEmpty()) return ""
        return applyMaskWithMapping(digits, maskGroups).formatted
    }

    fun processPhoneFieldInput(
        raw: String,
        currentCountry: CountryDialCode
    ): Pair<CountryDialCode, String> {
        val cleaned = cleanInput(raw)
        if (cleaned.isEmpty()) return currentCountry to ""

        if (cleaned.startsWith("+")) {
            val matched = countriesByCodeLengthDesc.firstOrNull { cleaned.startsWith(it.dialCode) }
            if (matched != null) {
                val localPart = cleaned.removePrefix(matched.dialCode)
                    .trimStart('0')
                    .take(matched.maxLocalDigits)
                return matched to localPart
            }
            return if (cleaned.length <= 4) {
                currentCountry to cleaned
            } else {
                currentCountry to cleaned.trimStart('+').trimStart('0').take(currentCountry.maxLocalDigits)
            }
        }

        if (cleaned.startsWith("00")) {
            return if (cleaned.length >= 5) {
                val asPlus = "+" + cleaned.substring(2)
                val matched = countriesByCodeLengthDesc.firstOrNull { asPlus.startsWith(it.dialCode) }
                if (matched != null) {
                    val localPart = asPlus.removePrefix(matched.dialCode)
                        .trimStart('0')
                        .take(matched.maxLocalDigits)
                    matched to localPart
                } else {
                    currentCountry to cleaned.drop(2).trimStart('0').take(currentCountry.maxLocalDigits)
                }
            } else {
                currentCountry to cleaned
            }
        }

        // Single national trunk '0' followed by local digit (e.g. 050... -> 50...)
        if (cleaned.startsWith("0") && cleaned.length >= 2) {
            val stripped = cleaned.trimStart('0').take(currentCountry.maxLocalDigits)
            return currentCountry to stripped
        }

        // Keep brief single '0' so user can still type '00...' international prefix
        if (cleaned == "0") {
            return currentCountry to "0"
        }

        val codeDigits = currentCountry.dialCode.removePrefix("+")
        val normalized = if (
            cleaned.startsWith(codeDigits) &&
            cleaned.length >= codeDigits.length + currentCountry.minLocalDigits
        ) {
            cleaned.removePrefix(codeDigits).trimStart('0')
        } else {
            cleaned
        }
        return currentCountry to normalized.take(currentCountry.maxLocalDigits)
    }

    fun detectCountryAndLocal(
        rawPhone: String,
        fallbackCountry: CountryDialCode = defaultCountry
    ): Pair<CountryDialCode, String> {
        val cleaned = cleanInput(rawPhone)
        val international = when {
            cleaned.startsWith("+") -> cleaned
            cleaned.startsWith("00") && cleaned.length > 4 -> "+" + cleaned.substring(2)
            else -> null
        }
        if (international != null) {
            val matched = countriesByCodeLengthDesc.firstOrNull { international.startsWith(it.dialCode) }
            if (matched != null) {
                val localPart = international.removePrefix(matched.dialCode)
                    .trimStart('0')
                    .take(matched.maxLocalDigits)
                return matched to localPart
            }
        }
        return fallbackCountry to cleaned.trimStart('+').trimStart('0').take(fallbackCountry.maxLocalDigits)
    }

    fun buildE164(selectedCountry: CountryDialCode, rawLocalOrFull: String): String {
        val cleaned = cleanInput(rawLocalOrFull)
        if (cleaned.isEmpty()) return ""
        if (cleaned.startsWith("+")) {
            return cleaned
        }
        if (cleaned.startsWith("00") && cleaned.length > 4) {
            return "+" + cleaned.substring(2)
        }
        val codeDigits = selectedCountry.dialCode.removePrefix("+")
        val withoutLeadingZeros = cleaned.trimStart('0')
        val normalizedLocal = if (
            withoutLeadingZeros.startsWith(codeDigits) &&
            withoutLeadingZeros.length - codeDigits.length >= 7
        ) {
            withoutLeadingZeros.removePrefix(codeDigits).trimStart('0')
        } else {
            withoutLeadingZeros
        }
        return if (normalizedLocal.isEmpty()) "" else "${selectedCountry.dialCode}$normalizedLocal"
    }

    fun isValidE164(e164: String): Boolean {
        return e164.matches(Regex("^\\+[1-9][0-9]{7,14}$"))
    }

    fun isCompleteForCountry(selectedCountry: CountryDialCode, rawLocalOrFull: String): Boolean {
        val e164 = buildE164(selectedCountry, rawLocalOrFull)
        if (!isValidE164(e164)) return false
        val localDigits = e164.removePrefix(selectedCountry.dialCode)
        return if (e164.startsWith(selectedCountry.dialCode)) {
            localDigits.length in selectedCountry.minLocalDigits..selectedCountry.maxLocalDigits
        } else {
            true
        }
    }
}

@Composable
fun PhoneCountryPickerField(
    selectedCountry: CountryDialCode,
    onCountrySelected: (CountryDialCode) -> Unit,
    localPhone: String,
    onLocalPhoneChange: (String) -> Unit,
    label: String = "رقم الهاتف",
    enabled: Boolean = true,
    isError: Boolean = false,
    testTagPrefix: String = "phone"
) {
    var showPickerDialog by remember { mutableStateOf(false) }
    val normalizedE164 = remember(selectedCountry, localPhone) {
        PhoneNumberHelper.buildE164(selectedCountry, localPhone)
    }
    val localDigitsOnly = remember(localPhone) {
        val cleaned = PhoneNumberHelper.cleanInput(localPhone)
        if (cleaned.startsWith("+") || cleaned.startsWith("00")) "" else cleaned.trimStart('0')
    }
    val isCompleteValid = remember(selectedCountry, localPhone, normalizedE164) {
        PhoneNumberHelper.isCompleteForCountry(selectedCountry, localPhone)
    }
    val visualTransformation = remember(selectedCountry.maskGroups) {
        PhoneMaskVisualTransformation(selectedCountry.maskGroups)
    }
    val formattedSample = remember(selectedCountry) {
        PhoneNumberHelper.formatLocalWithMask(selectedCountry.sampleLocal, selectedCountry.maskGroups)
    }
    val formattedDisplayLocal = remember(localDigitsOnly, selectedCountry.maskGroups) {
        PhoneNumberHelper.formatLocalWithMask(localDigitsOnly, selectedCountry.maskGroups)
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = { if (enabled) showPickerDialog = true },
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(
                        1.dp,
                        if (isError) MaterialTheme.colorScheme.error else GoldMetallic.copy(alpha = 0.7f)
                    ),
                    modifier = Modifier
                        .height(56.dp)
                        .testTag("${testTagPrefix}_country_selector")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(selectedCountry.flag, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = selectedCountry.dialCode,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            contentDescription = "اختيار مفتاح الدولة",
                            tint = GoldMetallic,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                OutlinedTextField(
                    value = localPhone,
                    onValueChange = { raw ->
                        val (nextCountry, nextLocal) = PhoneNumberHelper.processPhoneFieldInput(raw, selectedCountry)
                        if (nextCountry.iso != selectedCountry.iso) {
                            onCountrySelected(nextCountry)
                        }
                        onLocalPhoneChange(nextLocal)
                    },
                    enabled = enabled,
                    isError = isError,
                    label = { Text(label) },
                    placeholder = { Text(formattedSample) },
                    leadingIcon = { Icon(Icons.Filled.PhoneAndroid, contentDescription = null) },
                    visualTransformation = visualTransformation,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("${testTagPrefix}_local_input")
                )
            }
        }

        if (localPhone.isNotBlank()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (isCompleteValid) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = EmeraldVerified,
                        modifier = Modifier.size(14.dp)
                    )
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Text(
                            text = if (formattedDisplayLocal.isNotBlank()) {
                                "${selectedCountry.dialCode} $formattedDisplayLocal"
                            } else {
                                normalizedE164
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldVerified,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "(${selectedCountry.nameAr}) رقم صحيح جاهز للربط",
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldVerified
                    )
                } else {
                    val enteredCount = localDigitsOnly.length
                    Text(
                        text = "أكمل الصيغة (${enteredCount}/${selectedCountry.maxLocalDigits} أرقام — مثال: $formattedSample)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            Text(
                text = "الصيغة (${selectedCountry.flag} ${selectedCountry.nameAr}): ${selectedCountry.dialCode} ${selectedCountry.maskPattern} — مثال: $formattedSample",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showPickerDialog) {
        CountryPickerDialog(
            selectedCountry = selectedCountry,
            onSelect = { newCountry ->
                onCountrySelected(newCountry)
                val cleaned = PhoneNumberHelper.cleanInput(localPhone)
                if (!cleaned.startsWith("+") && !cleaned.startsWith("00") && cleaned.length > newCountry.maxLocalDigits) {
                    onLocalPhoneChange(cleaned.take(newCountry.maxLocalDigits))
                }
                showPickerDialog = false
            },
            onDismiss = { showPickerDialog = false }
        )
    }
}

@Composable
private fun CountryPickerDialog(
    selectedCountry: CountryDialCode,
    onSelect: (CountryDialCode) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredCountries = remember(searchQuery) {
        val q = PhoneNumberHelper.toAsciiDigits(searchQuery.trim())
        if (q.isEmpty()) {
            PhoneNumberHelper.countries
        } else {
            PhoneNumberHelper.countries.filter { country ->
                country.nameAr.contains(q, ignoreCase = true) ||
                    country.dialCode.contains(q) ||
                    country.dialCode.removePrefix("+").contains(q) ||
                    country.iso.contains(q, ignoreCase = true)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "اختر مفتاح الدولة",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it.take(40) },
                    label = { Text("ابحث باسم الدولة أو المفتاح (مثل 967 أو اليمن)") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("country_search_input")
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PhoneNumberHelper.quickCountries.forEach { quick ->
                        val selected = quick.iso == selectedCountry.iso
                        FilterChip(
                            selected = selected,
                            onClick = { onSelect(quick) },
                            label = {
                                Text("${quick.flag} ${quick.dialCode}")
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = GoldMetallic.copy(alpha = 0.22f),
                                selectedLabelColor = GoldMetallic
                            )
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filteredCountries, key = { it.iso }) { country ->
                        val isSelected = country.iso == selectedCountry.iso
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) {
                                GoldMetallic.copy(alpha = 0.16f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            },
                            border = if (isSelected) BorderStroke(1.dp, GoldMetallic) else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(country) }
                                .testTag("country_item_${country.iso}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(country.flag, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        text = country.nameAr,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                    Text(
                                        text = country.dialCode,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = GoldMetallic
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("إغلاق")
            }
        }
    )
}
