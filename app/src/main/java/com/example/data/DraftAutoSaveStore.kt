package com.example.data

import android.content.Context
import com.example.model.AgreementAttachment
import com.example.model.AgreementType
import com.example.model.CurrencyCode
import com.example.model.Party
import com.example.model.PaymentMethodType
import com.example.model.Witness
import org.json.JSONArray
import org.json.JSONObject

data class LocalDraftSnapshot(
    val existingAgreementId: String? = null,
    val stepIndex: Int = 0,
    val title: String = "",
    val type: AgreementType = AgreementType.PARTNERSHIP,
    val description: String = "",
    val currency: CurrencyCode = CurrencyCode.SAR,
    val amountText: String = "",
    val paymentMethod: PaymentMethodType = PaymentMethodType.BANK_TRANSFER,
    val agreementDate: String = "",
    val startDate: String = "",
    val dueDate: String = "",
    val parties: List<Party> = emptyList(),
    val rights: List<String> = emptyList(),
    val obligations: List<String> = emptyList(),
    val specialTerms: List<String> = emptyList(),
    val witnesses: List<Witness> = emptyList(),
    val attachments: List<AgreementAttachment> = emptyList(),
    val savedAtMillis: Long = 0L
)

object DraftAutoSaveStore {

    private const val PREFS_NAME = "meethaq_wizard_autosave"

    private fun keyFor(userId: String): String = "draft_v1_$userId"

    fun save(context: Context, userId: String, snapshot: LocalDraftSnapshot) {
        if (userId.isBlank()) return
        val json = JSONObject().apply {
            put("existingAgreementId", snapshot.existingAgreementId ?: "")
            put("stepIndex", snapshot.stepIndex)
            put("title", snapshot.title)
            put("type", snapshot.type.name)
            put("description", snapshot.description)
            put("currency", snapshot.currency.name)
            put("amountText", snapshot.amountText)
            put("paymentMethod", snapshot.paymentMethod.name)
            put("agreementDate", snapshot.agreementDate)
            put("startDate", snapshot.startDate)
            put("dueDate", snapshot.dueDate)
            put("savedAtMillis", snapshot.savedAtMillis)

            put("rights", JSONArray(snapshot.rights))
            put("obligations", JSONArray(snapshot.obligations))
            put("specialTerms", JSONArray(snapshot.specialTerms))

            put(
                "parties",
                JSONArray().apply {
                    snapshot.parties.forEach { p ->
                        put(
                            JSONObject().apply {
                                put("name", p.name)
                                put("email", p.email)
                                put("phone", p.phone)
                                put("role", p.role)
                            }
                        )
                    }
                }
            )

            put(
                "witnesses",
                JSONArray().apply {
                    snapshot.witnesses.forEach { w ->
                        put(
                            JSONObject().apply {
                                put("name", w.name)
                                put("email", w.email)
                                put("phone", w.phone)
                                put("nationalId", w.nationalId ?: "")
                            }
                        )
                    }
                }
            )

            put(
                "attachments",
                JSONArray().apply {
                    snapshot.attachments.forEach { att ->
                        put(
                            JSONObject().apply {
                                put("fileName", att.fileName)
                                put("mimeType", att.mimeType)
                                put("sizeBytes", att.sizeBytes)
                                put("sha256Hash", att.sha256Hash)
                            }
                        )
                    }
                }
            )
        }
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(keyFor(userId), json.toString())
            .apply()
    }

    fun load(context: Context, userId: String): LocalDraftSnapshot? {
        if (userId.isBlank()) return null
        val raw = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(keyFor(userId), null)
            ?: return null
        return try {
            val obj = JSONObject(raw)
            val existingId = obj.optString("existingAgreementId").takeIf { it.isNotBlank() }

            fun readStrings(name: String): List<String> {
                val arr = obj.optJSONArray(name) ?: return emptyList()
                return List(arr.length()) { idx -> arr.optString(idx) }.filter { it.isNotBlank() }
            }

            val partiesArr = obj.optJSONArray("parties") ?: JSONArray()
            val parties = List(partiesArr.length()) { idx ->
                val p = partiesArr.getJSONObject(idx)
                Party(
                    name = p.optString("name"),
                    email = p.optString("email"),
                    phone = p.optString("phone"),
                    role = p.optString("role", "طرف مشارك")
                )
            }

            val witnessesArr = obj.optJSONArray("witnesses") ?: JSONArray()
            val witnesses = List(witnessesArr.length()) { idx ->
                val w = witnessesArr.getJSONObject(idx)
                Witness(
                    name = w.optString("name"),
                    email = w.optString("email"),
                    phone = w.optString("phone"),
                    nationalId = w.optString("nationalId").takeIf { it.isNotBlank() }
                )
            }

            val attachArr = obj.optJSONArray("attachments") ?: JSONArray()
            val attachments = List(attachArr.length()) { idx ->
                val a = attachArr.getJSONObject(idx)
                AgreementAttachment(
                    fileName = a.optString("fileName"),
                    mimeType = a.optString("mimeType"),
                    sizeBytes = a.optLong("sizeBytes", 0L),
                    sha256Hash = a.optString("sha256Hash")
                )
            }

            LocalDraftSnapshot(
                existingAgreementId = existingId,
                stepIndex = obj.optInt("stepIndex", 0).coerceIn(0, 5),
                title = obj.optString("title"),
                type = AgreementType.fromRaw(obj.optString("type")),
                description = obj.optString("description"),
                currency = CurrencyCode.fromRaw(obj.optString("currency")),
                amountText = obj.optString("amountText"),
                paymentMethod = PaymentMethodType.fromRaw(obj.optString("paymentMethod")),
                agreementDate = obj.optString("agreementDate"),
                startDate = obj.optString("startDate"),
                dueDate = obj.optString("dueDate"),
                parties = parties,
                rights = readStrings("rights"),
                obligations = readStrings("obligations"),
                specialTerms = readStrings("specialTerms"),
                witnesses = witnesses,
                attachments = attachments,
                savedAtMillis = obj.optLong("savedAtMillis", 0L)
            )
        } catch (_: Exception) {
            null
        }
    }

    fun clear(context: Context, userId: String) {
        if (userId.isBlank()) return
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(keyFor(userId))
            .apply()
    }
}
