const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_1";
const ALICE_EMAIL = "alice@meethaq.app";
const BOB_UID = "bob_uid_2";
const BOB_EMAIL = "bob@meethaq.app";
const WITNESS_UID = "witness_uid_3";
const WITNESS_EMAIL = "witness@meethaq.app";
const STRANGER_UID = "stranger_uid_4";
const STRANGER_EMAIL = "stranger@meethaq.app";

const SAMPLE_HASH =
  "a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90";
const SAMPLE_APPROVED_HASH =
  "f0e1d2c3b4a5968778695a4b3c2d1e0ff0e1d2c3b4a5968778695a4b3c2d1e0f";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validAgreementPayload(overrides = {}) {
  const now = new Date(Date.now() - 2000);
  return {
    ownerId: ALICE_UID,
    ownerName: "أحمد العتيبي",
    ownerEmail: ALICE_EMAIL,
    agreementCode: "MTH-2026-9A4B2C",
    title: "اتفاقية تطوير وشراكة تقنية",
    type: "PARTNERSHIP",
    description: "اتفاق رسمي بين الطرفين لتطوير المنصة وتقاسم العوائد وفق البنود المحددة.",
    status: "PENDING",
    currency: "SAR",
    amount: 25000,
    paidTotal: 0,
    agreementDate: "2026-11-21",
    startDate: "2026-11-22",
    dueDate: "2026-12-31",
    paymentMethod: "BANK_TRANSFER",
    rights: ["حق استلام الدفعات في مواعيدها", "حق الاطلاع على التقارير"],
    obligations: ["الالتزام بالجدول الزمني", "السرية التامة"],
    specialTerms: ["يُفسخ العقد بالإخطار المسبق قبل 15 يوماً"],
    parties: [
      {
        uid: ALICE_UID,
        name: "أحمد العتيبي",
        email: ALICE_EMAIL,
        phone: "+966500000001",
        role: "الطرف الأول",
        verifiedPhone: true,
        biometricVerified: true,
        approved: true,
        credentialId: "cred_alice_123",
      },
      {
        name: "سالم الدوسري",
        email: BOB_EMAIL,
        phone: "+966500000002",
        role: "الطرف الثاني",
        verifiedPhone: false,
        biometricVerified: false,
        approved: false,
      },
    ],
    witnesses: [
      {
        name: "خالد الشمري",
        email: WITNESS_EMAIL,
        phone: "+966500000003",
        verifiedPhone: false,
        biometricVerified: false,
      },
    ],
    participantUids: [ALICE_UID],
    participantEmails: [ALICE_EMAIL, BOB_EMAIL, WITNESS_EMAIL],
    currentVersionId: "v1",
    versions: [
      {
        versionId: "v1",
        hash: SAMPLE_HASH,
        createdAtMillis: Date.now() - 2000,
        approvedBy: [ALICE_UID],
      },
    ],
    contentHash: SAMPLE_HASH,
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

function fullySignedParties() {
  return [
    {
      uid: ALICE_UID,
      name: "أحمد العتيبي",
      email: ALICE_EMAIL,
      phone: "+966500000001",
      role: "الطرف الأول",
      verifiedPhone: true,
      biometricVerified: true,
      approved: true,
      credentialId: "cred_alice_123",
    },
    {
      uid: BOB_UID,
      name: "سالم الدوسري",
      email: BOB_EMAIL,
      phone: "+966500000002",
      role: "الطرف الثاني",
      verifiedPhone: true,
      biometricVerified: true,
      approved: true,
      credentialId: "cred_bob_456",
    },
  ];
}

function fullySignedWitnesses() {
  return [
    {
      uid: WITNESS_UID,
      name: "خالد الشمري",
      email: WITNESS_EMAIL,
      phone: "+966500000003",
      verifiedPhone: true,
      biometricVerified: false,
      approvedAtMillis: Date.now() - 1000,
    },
  ];
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) await testEnv.cleanup();
});

beforeEach(async () => {
  if (testEnv) await testEnv.clearFirestore();
});

test("Users, private vault, and sessions strict isolation", async () => {
  const aliceDb = testEnv
    .authenticatedContext(ALICE_UID, { email: ALICE_EMAIL, email_verified: true })
    .firestore();
  const strangerDb = testEnv
    .authenticatedContext(STRANGER_UID, { email: STRANGER_EMAIL, email_verified: true })
    .firestore();
  const now = new Date(Date.now() - 2000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      fullName: "أحمد العتيبي",
      email: ALICE_EMAIL,
      phoneVerified: false,
      biometricCredentialId: "cred_alice_keystore_01",
      preferredTheme: "DARK",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("vault").doc("v_1").set({
      userId: ALICE_UID,
      title: "إيصال تحويلي",
      category: "إيصال",
      referenceCode: "VLT-100",
      sha256Hash: SAMPLE_HASH,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("sessions").doc("s_1").set({
      userId: ALICE_UID,
      device: "Pixel Android",
      userAgent: "MeethaqAndroid/1.0 (Android 15)",
      ipHash: SAMPLE_HASH.slice(0, 32),
      revoked: false,
      lastSeenAt: now,
      createdAt: now,
    })
  );

  // Stranger CANNOT read Alice's user doc, vault, or sessions
  await assertFails(strangerDb.collection("users").doc(ALICE_UID).get());
  await assertFails(strangerDb.collection("users").doc(ALICE_UID).collection("vault").get());
  await assertFails(strangerDb.collection("users").doc(ALICE_UID).collection("sessions").get());
});

test("Single party CANNOT finalize/approve before all parties & witnesses sign; unanimous approval locks against edits", async () => {
  const aliceDb = testEnv
    .authenticatedContext(ALICE_UID, { email: ALICE_EMAIL, email_verified: true })
    .firestore();
  const bobDb = testEnv
    .authenticatedContext(BOB_UID, { email: BOB_EMAIL, email_verified: true })
    .firestore();
  const witnessDb = testEnv
    .authenticatedContext(WITNESS_UID, { email: WITNESS_EMAIL, email_verified: true })
    .firestore();
  const strangerDb = testEnv
    .authenticatedContext(STRANGER_UID, { email: STRANGER_EMAIL, email_verified: true })
    .firestore();

  const agRef = aliceDb.collection("agreements").doc("ag_1");
  await assertSucceeds(agRef.set(validAgreementPayload()));

  // Stranger denied
  await assertFails(strangerDb.collection("agreements").doc("ag_1").get());

  // TEST 1 REQUIRED: Single party (Alice) tries to approve before Bob & Witness sign -> MUST FAIL
  const lockTime = new Date(Date.now() - 1000);
  await assertFails(
    agRef.update({
      status: "APPROVED",
      approvedSnapshot: { title: "اتفاقية تطوير وشراكة تقنية", contentHash: SAMPLE_HASH },
      approvedHash: SAMPLE_APPROVED_HASH,
      lockedAt: lockTime,
      updatedAt: lockTime,
    })
  );

  // Bob OTP challenge + signature
  const bobOtpRef = bobDb.collection("agreements").doc("ag_1").collection("approvals").doc("otp_bob");
  await assertSucceeds(
    bobOtpRef.set({
      agreementId: "ag_1",
      targetUid: BOB_UID,
      targetIdentifier: BOB_EMAIL,
      otpHash: SAMPLE_HASH,
      otpSalt: "slt_12345678",
      attempts: 0,
      maxAttempts: 5,
      used: false,
      verified: false,
      expiresAtMillis: Date.now() + 300000,
      participantUids: [ALICE_UID, BOB_UID],
      participantEmails: [ALICE_EMAIL, BOB_EMAIL, WITNESS_EMAIL],
      createdAt: new Date(Date.now() - 1500),
    })
  );
  await assertSucceeds(
    bobOtpRef.update({
      used: true,
      verified: true,
      verifiedAt: new Date(Date.now() - 1200),
    })
  );

  // Witness signs while PENDING
  await assertSucceeds(
    witnessDb.collection("agreements").doc("ag_1").update({
      witnesses: fullySignedWitnesses(),
      participantUids: [ALICE_UID, WITNESS_UID],
      updatedAt: new Date(Date.now() - 1100),
    })
  );

  // Bob signs and finalizes now that BOTH Alice & Bob & Witness signed -> SUCCEEDS
  await assertSucceeds(
    bobDb.collection("agreements").doc("ag_1").update({
      status: "APPROVED",
      parties: fullySignedParties(),
      witnesses: fullySignedWitnesses(),
      participantUids: [ALICE_UID, WITNESS_UID, BOB_UID],
      approvedSnapshot: {
        title: "اتفاقية تطوير وشراكة تقنية",
        amount: 25000,
        contentHash: SAMPLE_HASH,
      },
      approvedHash: SAMPLE_APPROVED_HASH,
      lockedAt: lockTime,
      updatedAt: lockTime,
    })
  );

  // TEST 2 REQUIRED: Direct post-approval edit by owner MUST FAIL
  await assertFails(
    aliceDb.collection("agreements").doc("ag_1").update({
      title: "عنوان معدّل بعد الاعتماد",
      amount: 99999,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Delete from UI MUST fail
  await assertFails(aliceDb.collection("agreements").doc("ag_1").delete());
});

test("Unanimous Amendment creates v2 on APPROVED agreement while preserving immutability", async () => {
  const aliceDb = testEnv
    .authenticatedContext(ALICE_UID, { email: ALICE_EMAIL, email_verified: true })
    .firestore();
  const bobDb = testEnv
    .authenticatedContext(BOB_UID, { email: BOB_EMAIL, email_verified: true })
    .firestore();

  const now = new Date(Date.now() - 2000);
  await assertSucceeds(
    aliceDb.collection("agreements").doc("ag_am").set(
      validAgreementPayload({
        witnesses: [],
        parties: fullySignedParties(),
        participantUids: [ALICE_UID, BOB_UID],
        participantEmails: [ALICE_EMAIL, BOB_EMAIL],
      })
    )
  );
  await assertSucceeds(
    bobDb.collection("agreements").doc("ag_am").update({
      status: "APPROVED",
      approvedSnapshot: { contentHash: SAMPLE_HASH },
      approvedHash: SAMPLE_APPROVED_HASH,
      lockedAt: now,
      updatedAt: now,
    })
  );

  const amendRef = aliceDb.collection("agreements").doc("ag_am").collection("amendments").doc("am_1");
  await assertSucceeds(
    amendRef.set({
      agreementId: "ag_am",
      baseVersionId: "v1",
      proposedByUid: ALICE_UID,
      proposedByName: "أحمد العتيبي",
      reason: "زيادة قيمة العقد لتغطية المرحلة الإضافية",
      proposedTitle: "اتفاقية تطوير وشراكة تقنية (محدّثة)",
      proposedDescription: "اتفاق رسمي محدّث بين الطرفين يشمل المرحلة الثانية والتقارير.",
      proposedAmount: 30000,
      proposedDueDate: "2027-01-31",
      proposedRights: ["حق استلام الدفعات في مواعيدها"],
      proposedObligations: ["الالتزام بالجدول الزمني"],
      proposedSpecialTerms: ["يُفسخ العقد بالإخطار المسبق قبل 15 يوماً"],
      diffItems: [{ fieldLabel: "المبلغ المالي", oldValue: "25000.00", newValue: "30000.00" }],
      requiredPartyKeys: [`uid:${ALICE_UID}`, `uid:${BOB_UID}`],
      approvedByUids: [ALICE_UID],
      approvedPartyKeys: [`uid:${ALICE_UID}`],
      status: "PENDING",
      participantUids: [ALICE_UID, BOB_UID],
      participantEmails: [ALICE_EMAIL, BOB_EMAIL],
      createdAt: new Date(Date.now() - 1500),
    })
  );

  // Unanimous Bob approval atomically updates both amendment and agreement to v2
  const applyBatch = bobDb.batch();
  applyBatch.update(bobDb.collection("agreements").doc("ag_am").collection("amendments").doc("am_1"), {
    approvedByUids: [ALICE_UID, BOB_UID],
    approvedPartyKeys: [`uid:${ALICE_UID}`, `uid:${BOB_UID}`],
    status: "APPROVED_APPLIED",
    appliedVersionId: "v2",
    resolvedAt: new Date(Date.now() - 1000),
  });
  applyBatch.update(bobDb.collection("agreements").doc("ag_am"), {
    title: "اتفاقية تطوير وشراكة تقنية (محدّثة)",
    amount: 30000,
    dueDate: "2027-01-31",
    currentVersionId: "v2",
    versions: [
      { versionId: "v1", hash: SAMPLE_HASH, createdAtMillis: Date.now() - 2000, approvedBy: [ALICE_UID, BOB_UID] },
      { versionId: "v2", hash: SAMPLE_APPROVED_HASH, createdAtMillis: Date.now() - 1000, approvedBy: [ALICE_UID, BOB_UID] },
    ],
    contentHash: SAMPLE_APPROVED_HASH,
    approvedHash: SAMPLE_APPROVED_HASH,
    approvedSnapshot: { title: "اتفاقية تطوير وشراكة تقنية (محدّثة)", amount: 30000 },
    lastAppliedAmendmentId: "am_1",
    lockedAt: new Date(Date.now() - 1000),
    updatedAt: new Date(Date.now() - 1000),
  });
  await assertSucceeds(applyBatch.commit());
});

test("Payments dual-proof confirmation, self-confirmation denied, confirmed payment immutable, append-only audit log", async () => {
  const aliceDb = testEnv
    .authenticatedContext(ALICE_UID, { email: ALICE_EMAIL, email_verified: true })
    .firestore();
  const bobDb = testEnv
    .authenticatedContext(BOB_UID, { email: BOB_EMAIL, email_verified: true })
    .firestore();

  const now = new Date(Date.now() - 2000);
  await assertSucceeds(
    aliceDb.collection("agreements").doc("ag_p2").set(
      validAgreementPayload({
        witnesses: [],
        parties: fullySignedParties(),
        participantUids: [ALICE_UID, BOB_UID],
        participantEmails: [ALICE_EMAIL, BOB_EMAIL],
      })
    )
  );
  await assertSucceeds(
    bobDb.collection("agreements").doc("ag_p2").update({
      status: "APPROVED",
      approvedSnapshot: { contentHash: SAMPLE_HASH },
      approvedHash: SAMPLE_APPROVED_HASH,
      lockedAt: now,
      updatedAt: now,
    })
  );

  // Alice records payment
  const payRef = aliceDb.collection("agreements").doc("ag_p2").collection("payments").doc("pay_1");
  await assertSucceeds(
    payRef.set({
      agreementId: "ag_p2",
      recordedBy: ALICE_UID,
      amount: 5000,
      paymentDateText: "2026-11-22",
      paidAt: now,
      method: "BANK_TRANSFER",
      receiptReference: "TRX-88412",
      confirmedBy: [ALICE_UID],
      status: "PENDING_CONFIRMATION",
      participantUids: [ALICE_UID, BOB_UID],
      participantEmails: [ALICE_EMAIL, BOB_EMAIL],
      createdAt: now,
    })
  );

  // Alice CANNOT self-confirm her own recorded payment without distinct recipient or witness+OTP
  await assertFails(
    payRef.update({
      confirmedBy: [ALICE_UID],
      confirmedAmount: 5000,
      confirmationMode: "FULL",
      status: "CONFIRMED",
    })
  );

  // Bob (recipient) confirms full receipt
  await assertSucceeds(
    bobDb.collection("agreements").doc("ag_p2").collection("payments").doc("pay_1").update({
      confirmedBy: [ALICE_UID, BOB_UID],
      confirmedAmount: 5000,
      confirmationMode: "FULL",
      status: "CONFIRMED",
    })
  );

  // TEST 3 REQUIRED: Confirmed payment CANNOT be modified or deleted
  await assertFails(
    aliceDb.collection("agreements").doc("ag_p2").collection("payments").doc("pay_1").update({
      amount: 100,
    })
  );
  await assertFails(
    bobDb.collection("agreements").doc("ag_p2").collection("payments").doc("pay_1").delete()
  );

  // Audit log append-only check with metadata
  const auRef = aliceDb.collection("agreements").doc("ag_p2").collection("audit").doc("au_1");
  await assertSucceeds(
    auRef.set({
      agreementId: "ag_p2",
      action: "PAYMENT_CONFIRMED",
      actorUid: ALICE_UID,
      actorName: "أحمد العتيبي",
      metadata: { amount: 5000, mode: "FULL" },
      snapshotHash: SAMPLE_HASH,
      participantUids: [ALICE_UID, BOB_UID],
      participantEmails: [ALICE_EMAIL, BOB_EMAIL],
      createdAt: new Date(Date.now() - 300),
    })
  );
  await assertFails(auRef.update({ action: "TAMPERED" }));
  await assertFails(auRef.delete());
});

test("OTP reuse prevention and strict vault isolation security checks", async () => {
  const aliceDb = testEnv
    .authenticatedContext(ALICE_UID, { email: ALICE_EMAIL, email_verified: true })
    .firestore();
  const strangerDb = testEnv
    .authenticatedContext(STRANGER_UID, { email: STRANGER_EMAIL, email_verified: true })
    .firestore();

  const now = new Date(Date.now() - 2000);
  await assertSucceeds(
    aliceDb.collection("agreements").doc("ag_otp").set(validAgreementPayload())
  );

  // Create OTP challenge for Alice
  const otpRef = aliceDb.collection("agreements").doc("ag_otp").collection("approvals").doc("otp_alice");
  await assertSucceeds(
    otpRef.set({
      agreementId: "ag_otp",
      targetUid: ALICE_UID,
      targetIdentifier: ALICE_EMAIL,
      otpHash: SAMPLE_HASH,
      otpSalt: "slt_reuse_test",
      attempts: 0,
      maxAttempts: 5,
      used: false,
      verified: false,
      expiresAtMillis: Date.now() + 300000,
      participantUids: [ALICE_UID],
      participantEmails: [ALICE_EMAIL],
      createdAt: now,
    })
  );

  // First verification marks it used = true and verified = true
  await assertSucceeds(
    otpRef.update({
      attempts: 1,
      used: true,
      verified: true,
      verifiedAt: now,
    })
  );

  // Attempting to reuse already used OTP must fail
  await assertFails(
    otpRef.update({
      attempts: 2,
      used: true,
      verified: true,
    })
  );

  // Stranger cannot read or write Alice's private vault
  await assertFails(
    strangerDb.collection("users").doc(ALICE_UID).collection("vault").doc("v_sec").set({
      userId: ALICE_UID,
      title: "اختراق",
      category: "مستند",
      referenceCode: "VLT-999",
      sha256Hash: SAMPLE_HASH,
      createdAt: now,
      updatedAt: now,
    })
  );

  // 1. Edit approved agreement must fail
  const approvedAgRef = aliceDb.collection("agreements").doc("ag_approved_test");
  await assertSucceeds(
    approvedAgRef.set(validAgreementPayload({
      status: "APPROVED",
      approvedHash: SAMPLE_APPROVED_HASH,
      approvedSnapshot: { title: "مغلق" },
      lockedAt: now
    }))
  );
  await assertFails(
    approvedAgRef.update({ title: "محاولة تعديل اتفاق معتمد" })
  );

  // 2. Delete agreement must fail
  await assertFails(approvedAgRef.delete());

  // 3 & 10. Access other user's files / private files must fail
  await assertFails(strangerDb.collection("users").doc(ALICE_UID).get());

  // 4. Change amounts on approved agreement must fail
  await assertFails(approvedAgRef.update({ amount: 999999 }));

  // 5. Forge approval in approvals subcollection must fail (Fix 1: write: if false)
  await assertFails(
    aliceDb.collection("agreements").doc("ag_otp").collection("approvals").doc("forged").set({
      agreementId: "ag_otp",
      targetUid: ALICE_UID,
      verified: true
    })
  );

  // 6. Reuse OTP already tested above (assertFails)

  // 7. Forge PDF certificate must fail (certificates write: if false)
  await assertFails(
    aliceDb.collection("certificates").doc("forged_cert").set({
      certId: "fake",
      status: "ACTIVE"
    })
  );

  // 8. Fake payment update must fail (Fix 2: payments update: if false)
  const payRef = aliceDb.collection("agreements").doc("ag_otp").collection("payments").doc("pay_1");
  await assertSucceeds(
    payRef.set({
      agreementId: "ag_otp",
      recordedBy: ALICE_UID,
      amount: 100,
      paidAt: now,
      method: "CASH",
      receiptReference: "REC-1",
      confirmedBy: [ALICE_UID],
      status: "PENDING_CONFIRMATION",
      participantUids: [ALICE_UID],
      participantEmails: [ALICE_EMAIL],
      createdAt: now
    })
  );
  await assertFails(payRef.update({ status: "CONFIRMED" }));

  // 9. Modify witness records without permission must fail
  await assertFails(
    strangerDb.collection("agreements").doc("ag_otp").update({
      witnesses: [{ name: "مزور" }]
    })
  );

  // 11. Expired invitation token update must fail
  const invRef = aliceDb.collection("invitations").doc("inv_expired");
  await assertSucceeds(
    invRef.set({
      agreementId: "ag_otp",
      agreementTitle: "عقد منتهي",
      inviterUid: ALICE_UID,
      inviterName: "أحمد",
      inviteePhone: "+966500000002",
      inviteeEmail: BOB_EMAIL,
      purpose: "PARTY",
      expiresAt: new Date(Date.now() - 10000), // expired
      createdAt: now
    })
  );
  await assertFails(
    bobDb.collection("invitations").doc("inv_expired").update({ usedAt: now })
  );

  // 12. Admin privilege bypass must fail for non-owner/non-member
  await assertFails(
    strangerDb.collection("agreements").doc("ag_otp").update({ title: "تجاوز صلاحيات المدير" })
  );
});


