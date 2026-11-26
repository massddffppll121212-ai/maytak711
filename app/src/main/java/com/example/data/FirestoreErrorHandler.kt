package com.example.data

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject

enum class OperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write"),
}

fun handleFirestoreError(
    exception: Throwable,
    operationType: OperationType,
    path: String?
): String {
    val causes = generateSequence(exception) { it.cause }
    val rootError = causes.firstOrNull { it is FirebaseFirestoreException }
        ?: causes.firstOrNull { it !is CancellationException }
        ?: exception

    val currentUser = Firebase.auth.currentUser
    val providerInfoList = currentUser?.providerData?.map { provider ->
        JSONObject().apply {
            put("providerId", provider.providerId)
            put("email", provider.email)
        }
    } ?: emptyList()

    val authInfoJson = JSONObject().apply {
        put("userId", currentUser?.uid)
        put("email", currentUser?.email)
        put("emailVerified", currentUser?.isEmailVerified)
        put("tenantId", currentUser?.tenantId)
        put("providerInfo", JSONArray(providerInfoList))
    }

    val errorInfoJson = JSONObject().apply {
        put("error", rootError.message ?: rootError.toString())
        put("operationType", operationType.value)
        put("path", path)
        put("authInfo", authInfoJson)
    }

    val jsonString = errorInfoJson.toString()
    Log.e("FirestoreError", "Firestore Error: $jsonString")
    return jsonString
}

fun toArabicErrorMessage(error: Throwable?): String {
    val firestoreException = generateSequence(error) { it.cause }
        .filterIsInstance<FirebaseFirestoreException>()
        .firstOrNull()

    return when (firestoreException?.code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED ->
            "ليس لديك صلاحية تنفيذ هذا الإجراء أو البيانات تخالف شروط التوثيق."
        FirebaseFirestoreException.Code.UNAVAILABLE ->
            "تعذّر الاتصال بالخادم السحابي حالياً. تحقق من اتصال الإنترنت وحاول مجدداً."
        FirebaseFirestoreException.Code.NOT_FOUND ->
            "لم يتم العثور على الوثيقة المطلوبة."
        FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED ->
            "تم بلوغ الحد اليومي المؤقت لعمليات القراءة/الكتابة في قاعدة البيانات."
        else ->
            error?.localizedMessage?.takeIf { it.isNotBlank() }
                ?: "حدث خطأ غير متوقع أثناء تنفيذ العملية."
    }
}
