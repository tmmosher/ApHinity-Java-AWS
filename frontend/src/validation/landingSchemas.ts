import { z, type ZodError } from "zod";

const FIELD_LABELS: Record<string, string> = {
  form: "Form",
  name: "Full name",
  email: "Email",
  password: "Password",
  captchaToken: "Captcha",
  code: "Verification code"
};

const emailSchema = z
  .string()
  .trim()
  .min(1, "Email is required")
  .email("Email must be a valid email address")
  .transform((value) => value.toLowerCase());

const passwordSchema = z.string().trim().min(1, "Password is required");

const signupPasswordSchema = passwordSchema
  .min(8, "Must be at least 8 characters")
  .regex(/\d/, "Must contain at least one digit")
  .regex(/\p{L}/u, "Must contain at least one letter")
  .regex(/[!@#$%^&*()_+\-={};':"\\|,.<>/?`~]/, "Must contain at least one special character");

const loginSchema = z.object({
  email: emailSchema,
  password: passwordSchema,
  captchaToken: z.string().trim().optional().default("")
});

const signupSchema = z.object({
  name: z.string().trim().min(1, "Full name is required"),
  email: emailSchema,
  password: signupPasswordSchema
});

const recoverySchema = z.object({
  email: emailSchema,
  captchaToken: z.string().trim().min(1, "Captcha is required")
});

const verifySchema = z.object({
  email: emailSchema,
  code: z
    .string()
    .trim()
    .min(1, "Verification code is required")
    .regex(/^\d{6}$/, "Verification code must be a 6-digit number")
});

export type LoginPayload = {
  email: string;
  password: string;
  captchaToken?: string;
};

export type SignupPayload = z.infer<typeof signupSchema>;
export type RecoveryPayload = z.infer<typeof recoverySchema>;
export type VerifyPayload = z.infer<typeof verifySchema>;

export class FieldError extends Error {
  readonly fields: string[];

  constructor(fields: string[]) {
    super(fields.join("; "));
    this.name = "FieldError";
    this.fields = fields;
  }

  static fromZodError(error: ZodError): FieldError {
    const fieldMessages = new Map<string, string>();

    for (const issue of error.issues) {
      const fieldName = issue.path.length > 0 ? String(issue.path[0]) : "form";
      if (fieldMessages.has(fieldName)) {
        continue;
      }
      fieldMessages.set(fieldName, issue.message);
    }

    const readableFields = Array.from(fieldMessages.entries()).map(([fieldName, message]) => {
      const label = FIELD_LABELS[fieldName] ?? fieldName;
      return `${label}: ${message}`;
    });

    return new FieldError(readableFields);
  }
}

export const parseLoginFormData = (
  formData: FormData,
  captchaRequired: boolean
): LoginPayload => {
  const parsed = parseWithFieldError(loginSchema, {
    email: getFormValue(formData, "email"),
    password: getFormValue(formData, "password"),
    captchaToken: getFormValue(formData, "cf-turnstile-response")
  });

  if (captchaRequired && parsed.captchaToken?.length === 0) {
    throw new FieldError(["Captcha: Captcha is required"]);
  }

  return {
    email: parsed.email,
    password: parsed.password,
    captchaToken: parsed.captchaToken || undefined
  };
};

export const parseSignupFormData = (formData: FormData): SignupPayload =>
  parseWithFieldError(signupSchema, {
    name: getFormValue(formData, "name"),
    email: getFormValue(formData, "email"),
    password: getFormValue(formData, "password")
  });

export const parseRecoveryFormData = (formData: FormData): RecoveryPayload =>
  parseWithFieldError(recoverySchema, {
    email: getFormValue(formData, "email"),
    captchaToken: getFormValue(formData, "cf-turnstile-response")
  });

export const parseVerifyFormData = (formData: FormData): VerifyPayload =>
  parseWithFieldError(verifySchema, {
    email: getFormValue(formData, "email"),
    code: getFormValue(formData, "verifyValue")
  });

const parseWithFieldError = <T>(schema: z.ZodType<T>, data: unknown): T => {
  const result = schema.safeParse(data);
  if (!result.success) {
    throw FieldError.fromZodError(result.error);
  }
  return result.data;
};

const getFormValue = (formData: FormData, fieldName: string): string => {
  const value = formData.get(fieldName);
  return typeof value === "string" ? value : "";
};
