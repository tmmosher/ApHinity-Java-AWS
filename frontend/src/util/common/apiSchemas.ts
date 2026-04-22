import {z} from "zod";

export const apiErrorPayloadSchema = z.object({
  code: z.string().optional(),
  message: z.string().optional(),
  error: z.string().optional(),
  status: z.number().optional(),
  path: z.string().optional()
});

const createNormalizedEmailSchema = (requiredMessage: string, invalidMessage: string) =>
  z
    .string()
    .trim()
    .min(1, requiredMessage)
    .email(invalidMessage)
    .transform((value) => value.toLowerCase());

const parseZodResult = <T>(result: z.SafeParseReturnType<unknown, T>, fallbackMessage: string): T => {
  if (result.success) {
    return result.data;
  }
  throw new Error(result.error.issues[0]?.message ?? fallbackMessage);
};

const inviteEmailSchema = createNormalizedEmailSchema(
  "Invite email is required.",
  "Invited email must be valid"
);

const workOrderEmailSchema = createNormalizedEmailSchema(
  "Work order email is required.",
  "Work order email must be valid"
);

const optionalWorkOrderEmailSchema = z.string().trim().transform((value, ctx) => {
  if (value.length === 0) {
    return null;
  }

  const result = workOrderEmailSchema.safeParse(value);
  if (!result.success) {
    ctx.addIssue({
      code: z.ZodIssueCode.custom,
      message: result.error.issues[0]?.message ?? "Work order email must be valid"
    });
    return z.NEVER;
  }

  return result.data;
});

export type ApiErrorPayload = z.infer<typeof apiErrorPayloadSchema>;

export const parseInviteEmail = (value: string): string =>
  parseZodResult(inviteEmailSchema.safeParse(value), "Invited email must be valid");

export const parseOptionalWorkOrderEmail = (value: string | null): string | null => {
  if (value === null) {
    return null;
  }

  return parseZodResult(optionalWorkOrderEmailSchema.safeParse(value), "Work order email must be valid");
};
