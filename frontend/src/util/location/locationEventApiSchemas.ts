import {z} from "zod";

export const serviceEventResponsibilitySchema = z.enum(["client", "partner"]);

export const serviceEventStatusSchema = z.enum(["upcoming", "current", "overdue", "completed"]);

export const apiErrorPayloadSchema = z.object({
  code: z.string().optional(),
  message: z.string().optional(),
  error: z.string().optional(),
  status: z.number().optional(),
  path: z.string().optional()
});

export const locationServiceEventSchema = z.object({
  id: z.number(),
  title: z.string(),
  responsibility: serviceEventResponsibilitySchema,
  date: z.string(),
  time: z.string(),
  endDate: z.string(),
  endTime: z.string(),
  description: z.string().nullable().optional().transform((value) => value ?? null),
  status: serviceEventStatusSchema,
  createdAt: z.string(),
  updatedAt: z.string()
});

export const locationServiceEventListSchema = z.array(locationServiceEventSchema);

export const serviceCalendarUploadResponseSchema = z.object({
  importedCount: z.number().min(0)
});
