import {z} from "zod";

export const apiErrorPayloadSchema = z.object({
  code: z.string().optional(),
  message: z.string().optional(),
  error: z.string().optional(),
  status: z.number().optional(),
  path: z.string().optional()
});

export const locationGanttTaskSchema = z.object({
  id: z.number(),
  title: z.string(),
  startDate: z.string(),
  endDate: z.string(),
  description: z.string().nullable().optional().transform((value) => value ?? null),
  createdAt: z.string(),
  updatedAt: z.string()
});

export const locationGanttTaskListSchema = z.array(locationGanttTaskSchema);
