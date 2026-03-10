import {
    ActiveInvite,
    InviteStatus,
    LocationGraph,
    LocationMembership, LocationMembershipWithStatus,
    LocationSectionLayout,
    LocationSectionLayoutConfig,
    LocationSummary
} from "../types/Types";

// Data verification helpers. Add any new data verification helpers here!

const isObject = (value: unknown): value is Record<string, unknown> =>
  !!value && typeof value === "object" && !Array.isArray(value);

const isInviteStatus = (value: unknown): value is InviteStatus =>
  value === "pending" || value === "accepted" || value === "revoked" || value === "expired";

const parseLocationSection = (value: unknown): LocationSectionLayout => {
  if (!isObject(value)) {
    throw new Error("Invalid location section");
  }
  if (
    typeof value.section_id !== "number" ||
    !Array.isArray(value.graph_ids) ||
    value.graph_ids.some((graphId) => typeof graphId !== "number")
  ) {
    throw new Error("Invalid location section shape");
  }
  return {
    section_id: value.section_id,
    graph_ids: value.graph_ids
  };
};

const parseLocationSectionLayout = (value: unknown): LocationSectionLayoutConfig => {
  if (!isObject(value) || !Array.isArray(value.sections)) {
    throw new Error("Invalid location section layout");
  }
  return {
    sections: value.sections.map(parseLocationSection)
  };
};

export const parseLocationSummary = (value: unknown): LocationSummary => {
  if (!isObject(value)) {
    throw new Error("Invalid location response");
  }
  if (
    typeof value.id !== "number" ||
    typeof value.name !== "string" ||
    typeof value.createdAt !== "string" ||
    typeof value.updatedAt !== "string"
  ) {
    throw new Error("Invalid location shape");
  }
  return {
    id: value.id,
    name: value.name,
    createdAt: value.createdAt,
    updatedAt: value.updatedAt,
    sectionLayout: parseLocationSectionLayout(value.sectionLayout ?? {
      sections: []
    })
  };
};

export const parseLocationList = (value: unknown): LocationSummary[] => {
  if (!Array.isArray(value)) {
    throw new Error("Invalid location list response");
  }
  return value.map(parseLocationSummary);
};

export const parseActiveInvite = (value: unknown): ActiveInvite => {
  if (!isObject(value)) {
    throw new Error("Invalid invite response");
  }
  if (
    typeof value.id !== "number" ||
    typeof value.locationId !== "number" ||
    (value.locationName !== null && typeof value.locationName !== "string") ||
    typeof value.invitedEmail !== "string" ||
    (value.invitedByUserId !== null && typeof value.invitedByUserId !== "number") ||
    !isInviteStatus(value.status) ||
    typeof value.expiresAt !== "string" ||
    typeof value.createdAt !== "string" ||
    (value.acceptedAt !== null && typeof value.acceptedAt !== "string") ||
    (value.acceptedUserId !== null && typeof value.acceptedUserId !== "number") ||
    (value.revokedAt !== null && typeof value.revokedAt !== "string")
  ) {
    throw new Error("Invalid invite shape");
  }
  return {
    id: value.id,
    locationId: value.locationId,
    locationName: value.locationName,
    invitedEmail: value.invitedEmail,
    invitedByUserId: value.invitedByUserId,
    status: value.status,
    expiresAt: value.expiresAt,
    createdAt: value.createdAt,
    acceptedAt: value.acceptedAt,
    acceptedUserId: value.acceptedUserId,
    revokedAt: value.revokedAt
  };
};

export const parseActiveInviteList = (value: unknown): ActiveInvite[] => {
  if (!Array.isArray(value)) {
    throw new Error("Invalid invite list response");
  }
  return value.map(parseActiveInvite);
};

export const parseLocationMembership = (value: unknown): LocationMembership => {
  if (!isObject(value)) {
    throw new Error("Invalid membership response");
  }
  if (
    typeof value.locationId !== "number" ||
    typeof value.userId !== "number" ||
    (value.userEmail !== null && typeof value.userEmail !== "string") ||
    typeof value.createdAt !== "string"
  ) {
    throw new Error("Invalid membership shape");
  }
  return {
    locationId: value.locationId,
    userId: value.userId,
    userEmail: value.userEmail,
    createdAt: value.createdAt
  };
};

const membershipDeletionQueue = (membership: LocationMembership) => {
    return ({membership, active: true}) as LocationMembershipWithStatus;
}

export const parseLocationMembershipList = (value: unknown): LocationMembershipWithStatus[] => {
  if (!Array.isArray(value)) {
    throw new Error("Invalid membership list response");
  }
  const locationMemberships = value.map(parseLocationMembership);
  return locationMemberships.map(membershipDeletionQueue);
};

const parseOptionalGraphObject = (
  value: unknown,
  errorMessage: string
): Record<string, unknown> | null | undefined => {
  if (value === undefined || value === null) {
    return value;
  }
  if (!isObject(value)) {
    throw new Error(errorMessage);
  }
  return value;
};

const parseGraphDataEntries = (value: unknown): Record<string, unknown>[] => {
  if (!Array.isArray(value) || value.some((entry) => !isObject(entry))) {
    throw new Error("Invalid graph data payload");
  }
  return value;
};

const parseLocationGraphColumns = (value: Record<string, unknown>) => {
  const topLevelLayout = parseOptionalGraphObject(
    value.layout,
    "Invalid graph layout payload"
  );
  const topLevelConfig = parseOptionalGraphObject(
    value.config,
    "Invalid graph config payload"
  );
  const topLevelStyle = parseOptionalGraphObject(
    value.style,
    "Invalid graph style payload"
  );

  // Preferred shape (DB-aligned): data/layout/config/style are independent fields.
  if (Array.isArray(value.data)) {
    return {
      data: parseGraphDataEntries(value.data),
      layout: topLevelLayout,
      config: topLevelConfig,
      style: topLevelStyle
    };
  }

  // Backward-compatible shape: data contains { data, layout, config, style }.
  if (!isObject(value.data)) {
    throw new Error("Invalid graph payload");
  }

  const nestedData = parseGraphDataEntries(value.data.data);
  const nestedLayout = parseOptionalGraphObject(
    value.data.layout,
    "Invalid graph layout payload"
  );
  const nestedConfig = parseOptionalGraphObject(
    value.data.config,
    "Invalid graph config payload"
  );
  const nestedStyle = parseOptionalGraphObject(
    value.data.style,
    "Invalid graph style payload"
  );

  return {
    data: nestedData,
    layout: topLevelLayout === undefined ? nestedLayout : topLevelLayout,
    config: topLevelConfig === undefined ? nestedConfig : topLevelConfig,
    style: topLevelStyle === undefined ? nestedStyle : topLevelStyle
  };
};

export const parseLocationGraph = (value: unknown): LocationGraph => {
  if (!isObject(value)) {
    throw new Error("Invalid graph response");
  }

  if (
    typeof value.id !== "number" ||
    typeof value.name !== "string" ||
    typeof value.createdAt !== "string" ||
    typeof value.updatedAt !== "string"
  ) {
    throw new Error("Invalid graph shape");
  }

  return {
    id: value.id,
    name: value.name,
    ...parseLocationGraphColumns(value),
    createdAt: value.createdAt,
    updatedAt: value.updatedAt
  };
};

export const parseLocationGraphList = (value: unknown): LocationGraph[] => {
  if (!Array.isArray(value)) {
    throw new Error("Invalid graph list response");
  }
  return value.map(parseLocationGraph);
};
