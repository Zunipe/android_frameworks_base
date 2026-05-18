package android.zunipe;

/** @hide */
interface IVerificationCallback {
    oneway void onVerificationCodeComing(String code);
}
