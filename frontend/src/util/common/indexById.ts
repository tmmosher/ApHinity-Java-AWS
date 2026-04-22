type Identifiable = {
  id: number;
};

export const createMapById = <T extends Identifiable>(items: readonly T[]): Map<number, T> => {
  const byId = new Map<number, T>();
  for (const item of items) {
    byId.set(item.id, item);
  }
  return byId;
};
