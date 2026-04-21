import {z} from "zod";
import {apiErrorPayloadSchema} from "../common/apiSchemas";

export {apiErrorPayloadSchema};

export const locationGanttTaskSchema = z.object({
  id: z.number(),
  title: z.string(),
  startDate: z.string(),
  endDate: z.string(),
  description: z.string().nullable().optional().transform((value) => value ?? null),
  createdAt: z.string(),
  updatedAt: z.string(),
  dependencyTaskIds: z.array(z.number()).default([])
});

export const locationGanttTaskListSchema = z.array(locationGanttTaskSchema);
