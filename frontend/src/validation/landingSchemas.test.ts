import { describe, expect, it } from "vitest";
import {
  FieldError,
  parseLoginFormData,
  parseRecoveryFormData,
  parseSignupFormData,
  parseVerifyFormData
} from "./landingSchemas";

const makeFormData = (values: Record<string, string>): FormData => {
  const formData = new FormData();
  for (const [key, value] of Object.entries(values)) {
    formData.set(key, value);
  }
  return formData;
};

describe("parseLoginFormData", () => {
  it("normalizes valid login payload", () => {
    const formData = makeFormData({
      email: "  USER@example.com  ",
      password: "  passWord1!  ",
      "cf-turnstile-response": ""
    });

    const payload = parseLoginFormData(formData, false);

    expect(payload).toEqual({
      email: "user@example.com",
      password: "passWord1!",
      captchaToken: undefined
    });
  });

  it("throws FieldError when email is invalid", () => {
    const formData = makeFormData({
      email: "bad-email",
      password: "Password1!"
    });

    let error: unknown;
    try {
      parseLoginFormData(formData, false);
    } catch (caught) {
      error = caught;
    }

    expect(error).toBeInstanceOf(FieldError);
    expect((error as FieldError).message).toContain("Email: Email must be a valid email address");
  });

  it("throws FieldError when captcha is required but missing", () => {
    const formData = makeFormData({
      email: "user@example.com",
      password: "Password1!",
      "cf-turnstile-response": ""
    });

    let error: unknown;
    try {
      parseLoginFormData(formData, true);
    } catch (caught) {
      error = caught;
    }

    expect(error).toBeInstanceOf(FieldError);
    expect((error as FieldError).message).toContain("Captcha: Captcha is required");
  });
});

describe("parseSignupFormData", () => {
  it("normalizes valid signup payload", () => {
    const formData = makeFormData({
      name: "  Jane Doe  ",
      email: "  JANE@Example.com  ",
      password: "  Abcdef1!  "
    });

    const payload = parseSignupFormData(formData);

    expect(payload).toEqual({
      name: "Jane Doe",
      email: "jane@example.com",
      password: "Abcdef1!"
    });
  });

  it("throws FieldError for password without special character", () => {
    const formData = makeFormData({
      name: "Jane Doe",
      email: "jane@example.com",
      password: "Abcdef12"
    });

    let error: unknown;
    try {
      parseSignupFormData(formData);
    } catch (caught) {
      error = caught;
    }

    expect(error).toBeInstanceOf(FieldError);
    expect((error as FieldError).message).toContain(
      "Password: Must contain at least one special character"
    );
  });
});

describe("parseRecoveryFormData", () => {
  it("normalizes valid recovery payload", () => {
    const formData = makeFormData({
      email: "  USER@Example.com  ",
      "cf-turnstile-response": "token-123"
    });

    const payload = parseRecoveryFormData(formData);

    expect(payload).toEqual({
      email: "user@example.com",
      captchaToken: "token-123"
    });
  });

  it("throws FieldError when captcha is missing", () => {
    const formData = makeFormData({
      email: "user@example.com",
      "cf-turnstile-response": "   "
    });

    let error: unknown;
    try {
      parseRecoveryFormData(formData);
    } catch (caught) {
      error = caught;
    }

    expect(error).toBeInstanceOf(FieldError);
    expect((error as FieldError).message).toContain("Captcha: Captcha is required");
  });
});

describe("parseVerifyFormData", () => {
  it("normalizes valid verify payload", () => {
    const formData = makeFormData({
      email: "  USER@Example.com  ",
      verifyValue: " 123456 "
    });

    const payload = parseVerifyFormData(formData);

    expect(payload).toEqual({
      email: "user@example.com",
      code: "123456"
    });
  });

  it("throws FieldError for non-6-digit code", () => {
    const formData = makeFormData({
      email: "user@example.com",
      verifyValue: "12a4"
    });

    let error: unknown;
    try {
      parseVerifyFormData(formData);
    } catch (caught) {
      error = caught;
    }

    expect(error).toBeInstanceOf(FieldError);
    expect((error as FieldError).message).toContain(
      "Verification code: Verification code must be a 6-digit number"
    );
  });
});
