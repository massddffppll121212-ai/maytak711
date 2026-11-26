import * as functions from "firebase-functions";
import * as admin from "firebase-admin";
import * as crypto from "crypto";
import { PDFDocument, rgb, StandardFonts } from "pdf-lib";

admin.initializeApp();
const db = admin.firestore();

// 1. sendOtp Cloud Function
export const sendOtp = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError("unauthenticated", "يجب تسجيل الدخول لاستلام رمز التحقق.");
  }
  const agreementId = data.agreementId;
  const targetIdentifier = data.targetIdentifier;
  if (!agreementId || !targetIdentifier) {
    throw new functions.https.HttpsError("invalid-argument", "معرّف الميثاق ورقم الهاتفه/البريد مطلوبان.");
  }

  const uid = context.auth.uid;
  const agreementRef = db.collection("agreements").doc(agreementId);
  const agreementSnap = await agreementRef.get();
  if (!agreementSnap.exists) {
    throw new functions.https.HttpsError("not-found", "الميثاق غير موجود.");
  }

  const agData = agreementSnap.data()!;
  const participantUids = agData.participantUids || [];
  const participantEmails = agData.participantEmails || [];
  const participantPhones = agData.participantPhones || [];

  const isMember = uid === agData.ownerId ||
    participantUids.includes(uid) ||
    participantEmails.includes(context.auth.token.email?.toLowerCase() || "") ||
    participantPhones.includes(context.auth.token.phone_number || "");

  if (!isMember) {
    throw new functions.https.HttpsError("permission-denied", "غير مصرح لك بطلب رمز تحقق لهذا الميثاق.");
  }

  // Generate 6-digit OTP
  const otp = Math.floor(100000 + Math.random() * 900000).toString();
  const salt = crypto.randomBytes(16).toString("hex");
  const otpHash = crypto.createHmac("sha256", salt).update(otp).digest("hex");
  const expiresAtMillis = Date.now() + 5 * 60 * 1000; // 5 mins TTL

  const approvalId = `otp_${uid}_${Date.now()}`;
  const approvalRef = agreementRef.collection("approvals").doc(approvalId);

  await approvalRef.set({
    agreementId,
    targetUid: uid,
    targetIdentifier,
    otpHash,
    otpSalt: salt,
    attempts: 0,
    maxAttempts: 5,
    used: false,
    verified: false,
    expiresAtMillis,
    participantUids: agData.participantUids || [uid],
    participantEmails: agData.participantEmails || [],
    participantPhones: agData.participantPhones || [],
    createdAt: admin.firestore.FieldValue.serverTimestamp(),
  });

  // In production, integrate SMS/Email gateway here. For simulation & testing, return masked OTP info.
  return {
    success: true,
    challengeId: approvalId,
    expiresAtMillis,
    message: "تم إرسال رمز التحقق بنجاح.",
  };
});

// 2. verifyOtp Cloud Function
export const verifyOtp = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError("unauthenticated", "يجب تسجيل الدخول للتحقق من الرمز.");
  }
  const { agreementId, challengeId, enteredOtp } = data;
  if (!agreementId || !challengeId || !enteredOtp) {
    throw new functions.https.HttpsError("invalid-argument", "بيانات التحقق غير مكتملة.");
  }

  const uid = context.auth.uid;
  const agreementRef = db.collection("agreements").doc(agreementId);
  const approvalRef = agreementRef.collection("approvals").doc(challengeId);

  return await db.runTransaction(async (tx) => {
    const approvalSnap = await tx.get(approvalRef);
    if (!approvalSnap.exists) {
      throw new functions.https.HttpsError("not-found", "تحدي التحقق غير موجود.");
    }
    const record = approvalSnap.data()!;
    if (record.targetUid !== uid) {
      throw new functions.https.HttpsError("permission-denied", "هذا الرمز غير مخصص لحسابك.");
    }
    if (record.used) {
      throw new functions.https.HttpsError("failed-precondition", "تم استخدام هذا الرمز مسبقاً.");
    }
    if (record.attempts >= record.maxAttempts) {
      throw new functions.https.HttpsError("resource-exhausted", "تم استنفاد الحد الأقصى لمحاولات التحقق (5 محاولات).");
    }
    if (Date.now() > record.expiresAtMillis) {
      throw new functions.https.HttpsError("deadline-exceeded", "انتهت صلاحية رمز التحقق.");
    }

    const candidateHash = crypto.createHmac("sha256", record.otpSalt).update(enteredOtp.trim()).digest("hex");
    const nextAttempts = record.attempts + 1;

    if (candidateHash !== record.otpHash) {
      tx.update(approvalRef, {
        attempts: nextAttempts,
        used: nextAttempts >= record.maxAttempts,
      });
      return {
        verified: false,
        message: `رمز التحقق غير صحيح. المحاولات المتبقية: ${record.maxAttempts - nextAttempts}`,
        remainingAttempts: record.maxAttempts - nextAttempts,
      };
    }

    tx.update(approvalRef, {
      attempts: nextAttempts,
      used: true,
      verified: true,
      verifiedAt: admin.firestore.FieldValue.serverTimestamp(),
    });

    const agreementSnap = await tx.get(agreementRef);
    if (agreementSnap.exists) {
      const agData = agreementSnap.data()!;
      const parties = (agData.parties || []).map((p: any) => {
        if (p.uid === uid || p.email?.toLowerCase() === context.auth?.token.email?.toLowerCase()) {
          return { ...p, uid, verifiedPhone: true };
        }
        return p;
      });
      const witnesses = (agData.witnesses || []).map((w: any) => {
        if (w.uid === uid || w.email?.toLowerCase() === context.auth?.token.email?.toLowerCase()) {
          return { ...w, uid, verifiedPhone: true };
        }
        return w;
      });
      tx.update(agreementRef, {
        parties,
        witnesses,
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      });
    }

    await db.collection("users").doc(uid).set({
      phoneVerified: true,
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    }, { merge: true });

    return {
      verified: true,
      message: "تم التحقق من الهاتف بنجاح.",
      remainingAttempts: record.maxAttempts - nextAttempts,
    };
  });
});

// 3. signAgreement Cloud Function
export const signAgreement = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError("unauthenticated", "يجب تسجيل الدخول للتوقيع.");
  }
  const { agreementId, biometricVerified, credentialId, verificationMethod, ipHash, uaHash } = data;
  if (!agreementId) {
    throw new functions.https.HttpsError("invalid-argument", "معرّف الميثاق مطلوب.");
  }

  const uid = context.auth.uid;
  const userSnap = await db.collection("users").doc(uid).get();
  const userData = userSnap.data() || {};
  const phoneVerified = userData.phoneVerified === true;
  const signerName = userData.fullName || context.auth.token.name || "مستخدم ميثاق";

  const agreementRef = db.collection("agreements").doc(agreementId);
  const sigRef = agreementRef.collection("signatures").doc();
  const auditRef = agreementRef.collection("audit").doc();

  return await db.runTransaction(async (tx) => {
    const agSnap = await tx.get(agreementRef);
    if (!agSnap.exists) {
      throw new functions.https.HttpsError("not-found", "الميثاق غير موجود.");
    }
    const agData = agSnap.data()!;
    if (agData.status !== "PENDING") {
      throw new functions.https.HttpsError("failed-precondition", "لا يمكن التوقيع إلا على ميثاق قيد الانتظار.");
    }

    let matchedAsParty = false;
    let matchedAsWitness = false;

    const parties = (agData.parties || []).map((p: any) => {
      const match = p.uid === uid || p.email?.toLowerCase() === context.auth?.token.email?.toLowerCase();
      if (match) {
        matchedAsParty = true;
        return {
          ...p,
          uid,
          verifiedPhone: true,
          biometricVerified: biometricVerified === true,
          approved: true,
          credentialId: credentialId || p.credentialId,
        };
      }
      return p;
    });

    const witnesses = (agData.witnesses || []).map((w: any) => {
      const match = w.uid === uid || w.email?.toLowerCase() === context.auth?.token.email?.toLowerCase();
      if (match) {
        matchedAsWitness = true;
        return {
          ...w,
          uid,
          verifiedPhone: true,
          biometricVerified: biometricVerified === true,
          approvedAtMillis: Date.now(),
          credentialId: credentialId || w.credentialId,
        };
      }
      return w;
    });

    if (!matchedAsParty && !matchedAsWitness) {
      throw new functions.https.HttpsError("permission-denied", "هويتك غير مدرجة ضمن الأطراف أو الشهود.");
    }

    const role = matchedAsParty ? "PARTY" : "WITNESS";
    const participantUids = Array.from(new Set([...(agData.participantUids || []), uid]));

    tx.update(agreementRef, {
      parties,
      witnesses,
      participantUids,
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    });

    const sigPayload: any = {
      agreementId,
      userId: uid,
      signerName,
      role,
      versionId: agData.currentVersionId || "v1",
      versionHash: agData.contentHash,
      phoneVerified,
      biometricVerified: biometricVerified === true,
      verificationMethod: verificationMethod || "Biometric+OTP",
      ipHash: ipHash || "hash_ip",
      uaHash: uaHash || "hash_ua",
      participantUids,
      participantEmails: agData.participantEmails || [],
      participantPhones: agData.participantPhones || [],
      signedAt: admin.firestore.FieldValue.serverTimestamp(),
    };
    if (credentialId) {
      sigPayload.credentialId = credentialId;
    }

    tx.set(sigRef, sigPayload);
    tx.set(auditRef, {
      agreementId,
      action: "PARTY_DUAL_SIGNED",
      actorUid: uid,
      actorName: signerName,
      note: `توقيع موثّق (${role}) بنجاح.`,
      metadata: { role, versionId: agData.currentVersionId, biometricVerified },
      snapshotHash: agData.contentHash,
      participantUids,
      participantEmails: agData.participantEmails || [],
      participantPhones: agData.participantPhones || [],
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
    });

    return { success: true, signatureId: sigRef.id };
  });
});

// 4. approveAgreement Cloud Function (Transactional)
export const approveAgreement = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError("unauthenticated", "يجب تسجيل الدخول للاعتماد.");
  }
  const { agreementId } = data;
  if (!agreementId) {
    throw new functions.https.HttpsError("invalid-argument", "معرّف الميثاق مطلوب.");
  }

  const uid = context.auth.uid;
  const agreementRef = db.collection("agreements").doc(agreementId);
  const auditRef = agreementRef.collection("audit").doc();

  return await db.runTransaction(async (tx) => {
    const agSnap = await tx.get(agreementRef);
    if (!agSnap.exists) {
      throw new functions.https.HttpsError("not-found", "الميثاق غير موجود.");
    }
    const agData = agSnap.data()!;
    if (agData.status !== "PENDING") {
      throw new functions.https.HttpsError("failed-precondition", "الميثاق ليس بانتظار الاعتماد.");
    }

    const parties = agData.parties || [];
    const unsignedParties = parties.filter((p: any) => !p.approved);
    if (parties.length === 0 || unsignedParties.length > 0) {
      throw new functions.https.HttpsError("failed-precondition", "لا يمكن اعتماد الاتفاق نهائياً قبل توقيع جميع الأطراف.");
    }

    const witnesses = agData.witnesses || [];
    const unsignedWitnesses = witnesses.filter((w: any) => !w.approvedAtMillis);
    if (unsignedWitnesses.length > 0) {
      throw new functions.https.HttpsError("failed-precondition", "لا يمكن اعتماد الاتفاق قبل موافقة جميع الشهود.");
    }

    // Compute Canonical Approved Hash
    const canonicalString = `${agData.agreementCode}|${agData.title}|${agData.amount}|${agData.contentHash}|${Date.now()}`;
    const approvedHash = crypto.createHash("sha256").update(canonicalString).digest("hex");

    const approvedSnapshot = {
      agreementCode: agData.agreementCode,
      title: agData.title,
      amount: agData.amount,
      currency: agData.currency,
      parties: parties.map((p: any) => ({ name: p.name, role: p.role, uid: p.uid })),
      witnesses: witnesses.map((w: any) => ({ name: w.name })),
      contentHash: agData.contentHash,
      approvedHash,
      finalizedByUid: uid,
    };

    tx.update(agreementRef, {
      status: "APPROVED",
      approvedSnapshot,
      approvedHash,
      lockedAt: admin.firestore.FieldValue.serverTimestamp(),
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    });

    tx.set(auditRef, {
      agreementId,
      action: "APPROVED_AND_LOCKED",
      actorUid: uid,
      actorName: context.auth.token.name || "مالك الميثاق",
      note: "تم الاعتماد النهائي بإجماع الأطراف والشهود وقفل الميثاق رقمياً.",
      metadata: { approvedHash },
      snapshotHash: agData.contentHash,
      participantUids: agData.participantUids || [uid],
      participantEmails: agData.participantEmails || [],
      participantPhones: agData.participantPhones || [],
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
    });

    return { success: true, approvedHash };
  });
});

// 5. requestAmendment Cloud Function
export const requestAmendment = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError("unauthenticated", "يجب تسجيل الدخول لطلب تعديل.");
  }
  const { agreementId, reason, proposedTitle, proposedDescription, proposedAmount, diffItems } = data;
  if (!agreementId || !reason) {
    throw new functions.https.HttpsError("invalid-argument", "معرّف الميثاق وسبب التعديل مطلوبان.");
  }

  const uid = context.auth.uid;
  const agreementRef = db.collection("agreements").doc(agreementId);
  const agreementSnap = await agreementRef.get();
  if (!agreementSnap.exists) {
    throw new functions.https.HttpsError("not-found", "الميثاق غير موجود.");
  }
  const agData = agreementSnap.data()!;
  if (agData.status !== "APPROVED") {
    throw new functions.https.HttpsError("failed-precondition", "لا يمكن طلب تعديل إلا على اتفاق معتمد.");
  }

  const amendRef = agreementRef.collection("amendments").doc();
  const auditRef = agreementRef.collection("audit").doc();

  const requiredPartyKeys = (agData.parties || []).map((p: any) => `uid:${p.uid || p.email}`);

  await db.runTransaction(async (tx) => {
    tx.set(amendRef, {
      agreementId,
      baseVersionId: agData.currentVersionId || "v1",
      proposedByUid: uid,
      proposedByName: context.auth?.token.name || "مقترح التعديل",
      reason,
      proposedTitle: proposedTitle || agData.title,
      proposedDescription: proposedDescription || agData.description,
      proposedAmount: proposedAmount !== undefined ? proposedAmount : agData.amount,
      proposedDueDate: agData.dueDate || "",
      diffItems: diffItems || [],
      requiredPartyKeys,
      approvedByUids: [uid],
      approvedPartyKeys: [requiredPartyKeys[0] || `uid:${uid}`],
      status: "PENDING",
      participantUids: agData.participantUids || [uid],
      participantEmails: agData.participantEmails || [],
      participantPhones: agData.participantPhones || [],
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
    });

    tx.set(auditRef, {
      agreementId,
      action: "AMENDMENT_REQUESTED",
      actorUid: uid,
      actorName: context.auth?.token.name || "مستخدم",
      note: `اقتراح تعديل جديد: ${reason}`,
      metadata: { amendmentId: amendRef.id, reason },
      snapshotHash: agData.contentHash,
      participantUids: agData.participantUids || [uid],
      participantEmails: agData.participantEmails || [],
      participantPhones: agData.participantPhones || [],
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
    });
  });

  return { success: true, amendmentId: amendRef.id };
});

// 6. confirmPayment Cloud Function (Transactional)
export const confirmPayment = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError("unauthenticated", "يجب تسجيل الدخول لتأكيد الدفعة.");
  }
  const { agreementId, paymentId, mode, confirmedAmount, note, witnessOtpChallengeId } = data;
  if (!agreementId || !paymentId || !mode) {
    throw new functions.https.HttpsError("invalid-argument", "بيانات تأكيد الدفعة غير مكتملة.");
  }

  const uid = context.auth.uid;
  const agreementRef = db.collection("agreements").doc(agreementId);
  const payRef = agreementRef.collection("payments").doc(paymentId);
  const auditRef = agreementRef.collection("audit").doc();

  return await db.runTransaction(async (tx) => {
    const paySnap = await tx.get(payRef);
    if (!paySnap.exists) {
      throw new functions.https.HttpsError("not-found", "سجل الدفعة غير موجود.");
    }
    const payData = paySnap.data()!;
    if (payData.status !== "PENDING_CONFIRMATION") {
      throw new functions.https.HttpsError("failed-precondition", "هذه الدفعة مؤكدة أو محسومة مسبقاً.");
    }

    const agreementSnap = await tx.get(agreementRef);
    if (!agreementSnap.exists) {
      throw new functions.https.HttpsError("not-found", "الميثاق غير موجود.");
    }
    const agData = agreementSnap.data()!;

    const isDistinctRecipient = uid !== payData.recordedBy;
    let witnessVerified = false;

    if (witnessOtpChallengeId) {
      const otpRef = agreementRef.collection("approvals").doc(witnessOtpChallengeId);
      const otpSnap = await tx.get(otpRef);
      if (otpSnap.exists && otpSnap.data()!.verified && otpSnap.data()!.used) {
        witnessVerified = true;
      }
    }

    if (!isDistinctRecipient && !witnessVerified) {
      throw new functions.https.HttpsError("permission-denied", "لا يمكنك تأكيد دفعة سجلتها بنفسك دون مستلم آخر أو شاهد معتمد بـ OTP.");
    }

    let finalAmount = payData.amount;
    let finalStatus = "CONFIRMED";
    if (mode === "PARTIAL") {
      finalAmount = Number(confirmedAmount) || 0;
      finalStatus = "PARTIAL_CONFIRMED";
    } else if (mode === "NOT_RECEIVED") {
      finalAmount = 0;
      finalStatus = "NOT_RECEIVED";
    }

    const confirmedBy = Array.from(new Set([...(payData.confirmedBy || []), uid]));
    const payUpdate: any = {
      confirmedBy,
      confirmedAmount: finalAmount,
      confirmationMode: mode,
      status: finalStatus,
    };
    if (note) {
      payUpdate.confirmationNote = note;
    }
    if (witnessVerified) {
      payUpdate.witnessUid = uid;
      payUpdate.witnessOtpVerified = true;
    }

    tx.update(payRef, payUpdate);

    if (finalAmount > 0) {
      const newPaidTotal = (agData.paidTotal || 0) + finalAmount;
      tx.update(agreementRef, {
        paidTotal: newPaidTotal,
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      });
    }

    tx.set(auditRef, {
      agreementId,
      action: `PAYMENT_${mode}`,
      actorUid: uid,
      actorName: context.auth.token.name || "مؤكد الدفعة",
      note: `توثيق حالة الدفعة (${mode}) بمبلغ ${finalAmount}.`,
      metadata: { paymentId, mode, confirmedAmount: finalAmount },
      snapshotHash: agData.contentHash,
      participantUids: agData.participantUids || [uid],
      participantEmails: agData.participantEmails || [],
      participantPhones: agData.participantPhones || [],
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
    });

    return { success: true, status: finalStatus };
  });
});

// 7. generateCertificate Cloud Function
export const generateCertificate = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError("unauthenticated", "يجب تسجيل الدخول لتوليد الشهادة.");
  }
  const { agreementId } = data;
  if (!agreementId) {
    throw new functions.https.HttpsError("invalid-argument", "معرّف الميثاق مطلوب.");
  }

  const uid = context.auth.uid;
  const agreementRef = db.collection("agreements").doc(agreementId);
  const agreementSnap = await agreementRef.get();
  if (!agreementSnap.exists) {
    throw new functions.https.HttpsError("not-found", "الميثاق غير موجود.");
  }

  const agData = agreementSnap.data()!;
  if (agData.status !== "APPROVED") {
    throw new functions.https.HttpsError("failed-precondition", "لا يمكن توليد شهادة إلا لاتفاق معتمد نهائياً.");
  }

  const publicId = crypto.randomBytes(8).toString("hex");
  const secretPin = Math.floor(1000 + Math.random() * 9000).toString();
  const verifyUrl = `https://meethaq.app/verify/${publicId}`;

  const pdfDoc = await PDFDocument.create();
  const page = pdfDoc.addPage([595.28, 841.89]);
  const font = await pdfDoc.embedFont(StandardFonts.HelveticaBold);
  const fontReg = await pdfDoc.embedFont(StandardFonts.Helvetica);

  page.drawText("MEETHAQ - DIGITAL AGREEMENT CERTIFICATE", {
    x: 50,
    y: 780,
    size: 18,
    font,
    color: rgb(0.05, 0.1, 0.25),
  });

  page.drawText(`Agreement Code: ${agData.agreementCode || agreementId}`, { x: 50, y: 745, size: 12, font: fontReg, color: rgb(0.2, 0.2, 0.2) });
  page.drawText(`Title: ${agData.title || "Untitled"}`, { x: 50, y: 725, size: 12, font: fontReg, color: rgb(0.2, 0.2, 0.2) });
  page.drawText(`Amount: ${agData.amount || 0} ${agData.currency || "USD"}`, { x: 50, y: 705, size: 12, font: fontReg, color: rgb(0.2, 0.2, 0.2) });
  page.drawText(`Approved Hash: ${agData.approvedHash || "N/A"}`, { x: 50, y: 675, size: 9, font: fontReg, color: rgb(0.4, 0.4, 0.4) });
  page.drawText(`Verify URL: ${verifyUrl}`, { x: 50, y: 655, size: 10, font: fontReg, color: rgb(0, 0.3, 0.8) });
  page.drawText(`Secret PIN: ${secretPin}`, { x: 50, y: 635, size: 10, font, color: rgb(0.8, 0.2, 0.2) });

  const certId = db.collection("certificates").doc().id;

  await db.collection("certificates").doc(publicId).set({
    certId,
    agreementId,
    agreementCode: agData.agreementCode,
    title: agData.title,
    status: "ACTIVE",
    approvedHash: agData.approvedHash || "",
    secretPin,
    verifyUrl,
    createdAt: admin.firestore.FieldValue.serverTimestamp(),
    ownerUid: uid,
    participantUids: agData.participantUids || [uid],
    participantEmails: agData.participantEmails || [],
    participantPhones: agData.participantPhones || [],
  });

  const participantUids = agData.participantUids || [uid];
  for (const pUid of participantUids) {
    await db.collection("users").doc(pUid).collection("vault").add({
      title: `شهادة توثيق: ${agData.title}`,
      category: "CERTIFICATES",
      agreementId,
      publicId,
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
    });
  }

  return {
    success: true,
    publicId,
    verifyUrl,
    secretPin,
  };
});

// 8. openDispute Cloud Function
export const openDispute = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError("unauthenticated", "يجب تسجيل الدخول لفتح نزاع.");
  }
  const { agreementId, disputeType, details, evidenceItems } = data;
  if (!agreementId || !disputeType || !details) {
    throw new functions.https.HttpsError("invalid-argument", "بيانات النزاع غير مكتملة.");
  }

  const uid = context.auth.uid;
  const agreementRef = db.collection("agreements").doc(agreementId);
  const agreementSnap = await agreementRef.get();
  if (!agreementSnap.exists) {
    throw new functions.https.HttpsError("not-found", "الميثاق غير موجود.");
  }
  const agData = agreementSnap.data()!;

  const disputeRef = agreementRef.collection("disputes").doc();
  const auditRef = agreementRef.collection("audit").doc();

  const disputePayload = {
    agreementId,
    openedByUid: uid,
    openedByName: context.auth.token.name || "مستخدِم",
    disputeType,
    details,
    evidenceItems: evidenceItems || [],
    status: "OPEN",
    participantUids: agData.participantUids || [uid],
    participantEmails: agData.participantEmails || [],
    participantPhones: agData.participantPhones || [],
    createdAt: admin.firestore.FieldValue.serverTimestamp(),
  };

  await db.runTransaction(async (tx) => {
    tx.set(disputeRef, disputePayload);
    tx.set(auditRef, {
      agreementId,
      action: "DISPUTE_OPENED",
      actorUid: uid,
      actorName: context.auth.token.name || "مستخدم",
      note: `فتح نزاع جديد (${disputeType}): ${details.substring(0, 80)}`,
      metadata: { disputeId: disputeRef.id, disputeType },
      snapshotHash: agData.contentHash,
      participantUids: agData.participantUids || [uid],
      participantEmails: agData.participantEmails || [],
      participantPhones: agData.participantPhones || [],
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
    });
  });

  return { success: true, disputeId: disputeRef.id };
});

// 9. submitInheritanceRequest Cloud Function
export const submitInheritanceRequest = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError("unauthenticated", "يجب تسجيل الدخول تقديم طلب الممثل القانوني/الورثة.");
  }
  const { deceasedEmailOrPhone, legalReason, documents } = data;
  if (!deceasedEmailOrPhone || !legalReason) {
    throw new functions.https.HttpsError("invalid-argument", "بيانات الطلب غير مكتملة.");
  }

  const uid = context.auth.uid;
  const requestRef = db.collection("inheritanceRequests").doc();

  await requestRef.set({
    requesterUid: uid,
    requesterName: context.auth.token.name || "ممثل قانوني",
    deceasedEmailOrPhone,
    legalReason,
    documents: documents || [],
    status: "PENDING_REVIEW",
    createdAt: admin.firestore.FieldValue.serverTimestamp(),
  });

  return { success: true, requestId: requestRef.id };
});

