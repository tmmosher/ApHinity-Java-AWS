import {z} from "zod";

export const apiErrorPayloadSchema = z.object({
  code: z.string().optional(),
  message: z.string().optional(),
  error: z.string().optional(),
  status: z.number().optional(),
  path: z.string().optional()
});

export type ApiErrorPayload = z.infer<typeof apiErrorPayloadSchema>;
