import {z} from "zod";
import {apiErrorPayloadSchema} from "../common/apiSchemas";

export {apiErrorPayloadSchema};

export const serviceEventResponsibilitySchema = z.enum(["client", "partner"]);

export const serviceEventStatusSchema = z.enum(["upcoming", "current", "overdue", "completed"]);

export const locationServiceEventSchema = z.object({
  id: z.number(),
  title: z.string(),
  responsibility: serviceEventResponsibilitySchema,
  date: z.string(),
  time: z.string(),
  endDate: z.string(),
  endTime: z.string(),
  description: z.string().nullable().optional(),
  status: serviceEventStatusSchema,
  isCorrectiveAction: z.boolean().optional(),
  correctiveAction: z.boolean().optional(),
  correctiveActionSourceEventId: z.number().nullable().optional(),
  correctiveActionSourceEventTitle: z.string().nullable().optional(),
  createdAt: z.string(),
  updatedAt: z.string()
}).transform((value) => ({
  id: value.id,
  title: value.title,
  responsibility: value.responsibility,
  date: value.date,
  time: value.time,
  endDate: value.endDate,
  endTime: value.endTime,
  description: value.description ?? null,
  status: value.status,
  isCorrectiveAction: value.isCorrectiveAction ?? value.correctiveAction === true,
  correctiveActionSourceEventId: value.correctiveActionSourceEventId ?? null,
  correctiveActionSourceEventTitle: value.correctiveActionSourceEventTitle ?? null,
  createdAt: value.createdAt,
  updatedAt: value.updatedAt
}));

export const locationServiceEventListSchema = z.array(locationServiceEventSchema);

export const serviceCalendarUploadResponseSchema = z.object({
  importedCount: z.number().min(0)
});
