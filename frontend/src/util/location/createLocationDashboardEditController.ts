import {createEffect, createMemo, createSignal, type Accessor, untrack} from "solid-js";
import {toast} from "solid-toast";
import type {LocationGraph, LocationSectionLayout, LocationSectionLayoutConfig, LocationSummary} from "../../types/Types";
import {createMapById} from "../common/indexById";
import {
  applyGraphPayloadEdit,
  buildChangedLocationGraphUpdates,
  cloneLocationGraphs,
  type GraphBaselineEntry,
  pruneDeletedLocationGraphState,
  reconcileLocationGraphRefreshState,
  type EditableGraphPayload
} from "../graph/graphEditor";
import {
  buildCreateLocationGraphRequest,
  buildPostCreateGraphUpdate,
  type GraphCreateModalRequest
} from "../graph/graphCreate";
import {
  areLocationSectionLayoutsEqual,
  cloneLocationSectionLayout
} from "./dashboardLayoutEdit";
import {
  createLocationGraphById,
  deleteLocationGraphById,
  renameLocationGraphById,
  saveLocationGraphsById
} from "../graph/locationDetailApi";

type LocationDashboardEditControllerProps = {
  host: string;
  locationId: Accessor<string>;
  location: Accessor<LocationSummary | undefined>;
  graphs: Accessor<LocationGraph[] | undefined>;
  refetchLocation: () => Promise<unknown>;
  refetchGraphs: () => Promise<unknown>;
  canEditGraphs: Accessor<boolean>;
  shouldResetDashboardState: (nextLocationId: string) => boolean;
};

type DashboardSnapshot = {
  graphs: LocationGraph[];
  sectionLayout: LocationSectionLayoutConfig;
};

export const createLocationDashboardEditController = (props: LocationDashboardEditControllerProps) => {
  const [workingGraphs, setWorkingGraphs] = createSignal<LocationGraph[]>([]);
  const [graphBaselineIndex, setGraphBaselineIndex] = createSignal<Map<number, GraphBaselineEntry>>(new Map());
  const [workingSectionLayout, setWorkingSectionLayout] = createSignal<LocationSectionLayoutConfig>({sections: []});
  const [sectionLayoutBaseline, setSectionLayoutBaseline] = createSignal<LocationSectionLayoutConfig>({sections: []});
  const [sectionLayoutSyncUpdatedAt, setSectionLayoutSyncUpdatedAt] = createSignal<string | null>(null);
  const [hasInitializedSectionLayout, setHasInitializedSectionLayout] = createSignal(false);
  const [dashboardUndoStack, setDashboardUndoStack] = createSignal<DashboardSnapshot[]>([]);
  const [editingGraphId, setEditingGraphId] = createSignal<number | null>(null);
  const [isCreateGraphModalOpen, setIsCreateGraphModalOpen] = createSignal(false);
  const [isLayoutEditorOpen, setIsLayoutEditorOpen] = createSignal(false);
  const [isCreatingGraph, setIsCreatingGraph] = createSignal(false);
  const [isDeletingGraph, setIsDeletingGraph] = createSignal(false);
  const [isSavingGraphChanges, setIsSavingGraphChanges] = createSignal(false);
  const [locationSessionToken, setLocationSessionToken] = createSignal(0);
  const hasPendingDashboardChanges = createMemo(() => dashboardUndoStack().length > 0);
  const pendingDashboardMutationCount = createMemo(() => dashboardUndoStack().length);
  const hasPendingGraphChanges = hasPendingDashboardChanges;
  const isGraphMutationBusy = createMemo(() =>
    isSavingGraphChanges() || isCreatingGraph() || isDeletingGraph()
  );
  const graphById = createMemo(() => createMapById(workingGraphs()));
  const editingGraph = createMemo(() => {
    const graphId = editingGraphId();
    if (graphId === null) {
      return undefined;
    }
    return graphById().get(graphId);
  });
  const orderedSections = createMemo(() => workingSectionLayout().sections);
  const canCreateGraphs = createMemo(() =>
    props.canEditGraphs() && !hasPendingDashboardChanges() && !isGraphMutationBusy()
  );
  const sectionOptions = createMemo(() =>
    orderedSections().map((section) => ({
      id: section.section_id,
      label: `Section ${section.section_id} (${section.graph_ids.length} graph${section.graph_ids.length === 1 ? "" : "s"})`
    }))
  );
  const nextSectionId = createMemo(() => {
    const sections = orderedSections();
    return sections.length === 0 ? 1 : Math.max(...sections.map((section) => section.section_id)) + 1;
  });

  createEffect(() => {
    const fetchedGraphs = props.graphs();
    if (!fetchedGraphs) {
      return;
    }

    const refreshState = untrack(() => reconcileLocationGraphRefreshState(
      workingGraphs(),
      dashboardUndoStack().map((snapshot) => snapshot.graphs),
      graphBaselineIndex(),
      fetchedGraphs
    ));
    setWorkingGraphs(refreshState.nextGraphs);
    setGraphBaselineIndex(refreshState.nextBaselineIndex);
    setDashboardUndoStack((currentUndoStack) => currentUndoStack.map((snapshot, index) => ({
      graphs: refreshState.nextUndoStack[index] ?? snapshot.graphs,
      sectionLayout: snapshot.sectionLayout
    })));
  });

  createEffect(() => {
    const currentLocation = props.location();
    if (!currentLocation) {
      return;
    }

    if (sectionLayoutSyncUpdatedAt() === currentLocation.updatedAt) {
      return;
    }

    const serverLayout = cloneLocationSectionLayout(currentLocation.sectionLayout);
    if (!hasInitializedSectionLayout()) {
      setWorkingSectionLayout(serverLayout);
      setSectionLayoutBaseline(serverLayout);
      setSectionLayoutSyncUpdatedAt(currentLocation.updatedAt);
      setHasInitializedSectionLayout(true);
      return;
    }

    if (areLocationSectionLayoutsEqual(workingSectionLayout(), sectionLayoutBaseline())) {
      setWorkingSectionLayout(serverLayout);
      setSectionLayoutBaseline(serverLayout);
      setSectionLayoutSyncUpdatedAt(currentLocation.updatedAt);
    }
  });

  createEffect(() => {
    if (!props.shouldResetDashboardState(props.locationId())) {
      return;
    }

    setEditingGraphId(null);
    setIsCreateGraphModalOpen(false);
    setIsLayoutEditorOpen(false);
    setIsCreatingGraph(false);
    setIsDeletingGraph(false);
    setIsSavingGraphChanges(false);
    setWorkingGraphs([]);
    setGraphBaselineIndex(new Map());
    setWorkingSectionLayout({sections: []});
    setSectionLayoutBaseline({sections: []});
    setSectionLayoutSyncUpdatedAt(null);
    setHasInitializedSectionLayout(false);
    setDashboardUndoStack([]);
  });

  createEffect(() => {
    const graphId = editingGraphId();
    if (graphId === null) {
      return;
    }
    if (!graphById().has(graphId)) {
      setEditingGraphId(null);
    }
  });

  const closeGraphEditor = () => setEditingGraphId(null);
  const closeCreateGraphModal = () => setIsCreateGraphModalOpen(false);
  const closeLayoutEditor = () => setIsLayoutEditorOpen(false);

  const openGraphEditor = (graphId: number) => {
    if (isGraphMutationBusy() || !props.canEditGraphs()) {
      return;
    }
    setEditingGraphId(graphId);
  };

  const openCreateGraphModal = () => {
    if (!canCreateGraphs()) {
      return;
    }
    setIsCreateGraphModalOpen(true);
  };

  const openLayoutEditor = () => {
    if (isGraphMutationBusy() || !props.canEditGraphs()) {
      return;
    }
    setIsLayoutEditorOpen(true);
  };

  const sectionGraphs = (section: LocationSectionLayout): LocationGraph[] =>
    section.graph_ids
      .map((graphId) => graphById().get(graphId))
      .filter((graph): graph is LocationGraph => graph !== undefined);

  const missingGraphIds = (section: LocationSectionLayout): number[] =>
    section.graph_ids.filter((graphId) => !graphById().has(graphId));

  const createDashboardSnapshot = (): DashboardSnapshot => ({
    graphs: cloneLocationGraphs(workingGraphs()),
    sectionLayout: cloneLocationSectionLayout(workingSectionLayout())
  });

  const applyLocalGraphEdit = (graphId: number, payload: EditableGraphPayload) => {
    if (isGraphMutationBusy() || !props.canEditGraphs()) {
      return;
    }

    const result = applyGraphPayloadEdit(workingGraphs(), [], graphId, payload);
    if (!result.changed) {
      return;
    }

    setDashboardUndoStack((currentUndoStack) => [
      ...currentUndoStack,
      createDashboardSnapshot()
    ]);
    setWorkingGraphs(result.nextGraphs);
  };

  const applyLocalSectionLayoutEdit = (nextSectionLayout: LocationSectionLayoutConfig) => {
    if (isGraphMutationBusy() || !props.canEditGraphs()) {
      return;
    }
    if (areLocationSectionLayoutsEqual(workingSectionLayout(), nextSectionLayout)) {
      return;
    }

    setDashboardUndoStack((currentUndoStack) => [
      ...currentUndoStack,
      createDashboardSnapshot()
    ]);
    setWorkingSectionLayout(cloneLocationSectionLayout(nextSectionLayout));
  };

  const renameGraphFromModal = async (graphId: number, name: string): Promise<void> => {
    if (isGraphMutationBusy() || !props.canEditGraphs()) {
      throw new Error("Unable to rename graph.");
    }

    const result = await renameLocationGraphById(props.host, props.locationId(), graphId, name);
    setWorkingGraphs((currentGraphs) =>
      currentGraphs.map((graph) =>
        graph.id === graphId
          ? {...graph, name: result.name, updatedAt: result.updatedAt}
          : graph
      )
    );
    setDashboardUndoStack((currentUndoStack) =>
      currentUndoStack.map((snapshot) => ({
        ...snapshot,
        graphs: snapshot.graphs.map((graph) =>
          graph.id === graphId
            ? {...graph, name: result.name, updatedAt: result.updatedAt}
            : graph
        )
      }))
    );
    setGraphBaselineIndex((currentIndex) => {
      const nextIndex = new Map(currentIndex);
      const existingEntry = nextIndex.get(graphId);
      if (existingEntry) {
        nextIndex.set(graphId, {...existingEntry, expectedUpdatedAt: result.updatedAt});
      }
      return nextIndex;
    });

    try {
      await props.refetchLocation();
    } catch {
      toast.error("Graph renamed, but location details could not refresh. Please refresh the page.");
    }
  };

  const createGraphFromModal = async (request: GraphCreateModalRequest): Promise<void> => {
    if (isGraphMutationBusy() || hasPendingDashboardChanges() || !props.canEditGraphs()) {
      throw new Error("Unable to create graph.");
    }

    const createLocationId = props.locationId();
    const createSessionToken = locationSessionToken();
    let createdGraph: LocationGraph | null = null;
    setIsCreatingGraph(true);

    try {
      createdGraph = await createLocationGraphById(
        props.host,
        createLocationId,
        buildCreateLocationGraphRequest(request)
      );

      if (createLocationId !== props.locationId() || createSessionToken !== locationSessionToken()) {
        return;
      }

      const postCreateUpdate = buildPostCreateGraphUpdate(createdGraph, request);
      if (postCreateUpdate) {
        await saveLocationGraphsById(props.host, createLocationId, [postCreateUpdate]);
        if (createLocationId !== props.locationId() || createSessionToken !== locationSessionToken()) {
          return;
        }
      }

      setIsCreateGraphModalOpen(false);
      toast.success("Graph created.");

      try {
        await Promise.all([props.refetchGraphs(), props.refetchLocation()]);
        if (createLocationId !== props.locationId() || createSessionToken !== locationSessionToken()) {
          return;
        }
      } catch {
        if (createLocationId === props.locationId() && createSessionToken === locationSessionToken()) {
          toast.error("Graph created, but automatic refresh failed. Please refresh the page.");
        }
      }
    } catch (error) {
      if (createLocationId !== props.locationId() || createSessionToken !== locationSessionToken()) {
        return;
      }
      if (createdGraph) {
        setIsCreateGraphModalOpen(false);
        toast.error(resolveGraphCreateFollowUpFailureMessage(error));
        try {
          await Promise.all([props.refetchGraphs(), props.refetchLocation()]);
        } catch {
          if (createLocationId === props.locationId() && createSessionToken === locationSessionToken()) {
            toast.error("Graph created, but automatic refresh failed. Please refresh the page.");
          }
        }
        return;
      }

      if (error instanceof Error && error.message === "CSRF invalid") {
        throw new Error("Security token refresh failed. Please retry creating the graph.");
      }
      if (error instanceof Error && error.message === "Security token rejected") {
        throw new Error("Security validation failed. Retrying usually succeeds.");
      }
      if (error instanceof Error && error.message === "Authentication required") {
        throw new Error("Session refresh failed. Please sign in again.");
      }
      if (error instanceof Error && error.message === "Insufficient permissions") {
        throw new Error("You no longer have permission to create graphs.");
      }
      if (error instanceof Error && error.message === "Location section not found") {
        throw new Error("Selected section is no longer available. Please refresh and try again.");
      }

      throw error instanceof Error ? error : new Error("Unable to create graph.");
    } finally {
      if (createLocationId === props.locationId() && createSessionToken === locationSessionToken()) {
        setIsCreatingGraph(false);
      }
    }
  };

  const deleteGraphFromModal = async (graphId: number): Promise<void> => {
    if (hasPendingDashboardChanges()) {
      throw new Error("Apply or undo your pending dashboard changes before deleting a graph.");
    }
    if (isGraphMutationBusy() || !props.canEditGraphs()) {
      throw new Error("Unable to delete graph.");
    }

    const deleteLocationId = props.locationId();
    const deleteSessionToken = locationSessionToken();
    setIsDeletingGraph(true);

    try {
      await deleteLocationGraphById(props.host, deleteLocationId, graphId);

      if (deleteLocationId !== props.locationId() || deleteSessionToken !== locationSessionToken()) {
        return;
      }

      const cleanupResult = pruneDeletedLocationGraphState(
        workingGraphs(),
        dashboardUndoStack().map((snapshot) => snapshot.graphs),
        graphBaselineIndex(),
        graphId
      );
      setWorkingGraphs(cleanupResult.nextGraphs);
      setDashboardUndoStack((currentUndoStack) => currentUndoStack.length > 0 ? [] : currentUndoStack);
      setGraphBaselineIndex(cleanupResult.nextBaselineIndex);
      setEditingGraphId(null);
      toast.success("Graph deleted.");

      try {
        await Promise.all([props.refetchGraphs(), props.refetchLocation()]);
        if (deleteLocationId !== props.locationId() || deleteSessionToken !== locationSessionToken()) {
          return;
        }
      } catch {
        if (deleteLocationId === props.locationId() && deleteSessionToken === locationSessionToken()) {
          toast.error("Graph deleted, but automatic refresh failed. Please refresh the page.");
        }
      }
    } catch (error) {
      if (deleteLocationId !== props.locationId() || deleteSessionToken !== locationSessionToken()) {
        return;
      }

      if (error instanceof Error && error.message === "CSRF invalid") {
        throw new Error("Security token refresh failed. Please retry deleting the graph.");
      }
      if (error instanceof Error && error.message === "Security token rejected") {
        throw new Error("Security validation failed. Retrying usually succeeds.");
      }
      if (error instanceof Error && error.message === "Authentication required") {
        throw new Error("Session refresh failed. Please sign in again.");
      }
      if (error instanceof Error && error.message === "Insufficient permissions") {
        throw new Error("You no longer have permission to delete graphs.");
      }
      if (error instanceof Error && error.message === "Location graph not found") {
        throw new Error("Selected graph is no longer available. Please refresh and try again.");
      }

      throw error instanceof Error ? error : new Error("Unable to delete graph.");
    } finally {
      if (deleteLocationId === props.locationId() && deleteSessionToken === locationSessionToken()) {
        setIsDeletingGraph(false);
      }
    }
  };

  const undoLastDashboardEdit = () => {
    if (isGraphMutationBusy()) {
      return;
    }

    const currentUndoStack = dashboardUndoStack();
    if (currentUndoStack.length === 0) {
      return;
    }

    const nextSnapshot = currentUndoStack[currentUndoStack.length - 1];
    setWorkingGraphs(cloneLocationGraphs(nextSnapshot.graphs));
    setWorkingSectionLayout(cloneLocationSectionLayout(nextSnapshot.sectionLayout));
    setDashboardUndoStack(currentUndoStack.slice(0, -1));
  };

  const applyGraphChanges = async () => {
    if (isGraphMutationBusy() || !hasPendingDashboardChanges() || !props.canEditGraphs()) {
      return;
    }

    const saveLocationId = props.locationId();
    const saveSessionToken = locationSessionToken();
    const graphUpdates = buildChangedLocationGraphUpdates(workingGraphs(), graphBaselineIndex());
    const sectionLayoutUpdate = cloneLocationSectionLayout(workingSectionLayout());

    setIsSavingGraphChanges(true);
    try {
      await saveLocationGraphsById(props.host, saveLocationId, graphUpdates, sectionLayoutUpdate);
      if (saveLocationId !== props.locationId() || saveSessionToken !== locationSessionToken()) {
        return;
      }
      setSectionLayoutSyncUpdatedAt(props.location()?.updatedAt ?? null);
      setDashboardUndoStack([]);
      setSectionLayoutBaseline(cloneLocationSectionLayout(workingSectionLayout()));
      toast.success("Dashboard changes saved.");
      try {
        await Promise.all([props.refetchGraphs(), props.refetchLocation()]);
        if (saveLocationId !== props.locationId() || saveSessionToken !== locationSessionToken()) {
          return;
        }
      } catch {
        if (saveLocationId === props.locationId() && saveSessionToken === locationSessionToken()) {
          toast.error("Saved successfully, but automatic refresh failed. Please refresh the page.");
        }
      }
    } catch (error) {
      if (saveLocationId !== props.locationId() || saveSessionToken !== locationSessionToken()) {
        return;
      }
      if (error instanceof Error && error.message === "Graph update conflict") {
        try {
          await props.refetchGraphs();
          await props.refetchLocation();
          if (saveLocationId !== props.locationId() || saveSessionToken !== locationSessionToken()) {
            return;
          }
          toast.error("A graph was updated by another user. Latest data was reloaded.");
        } catch {
          if (saveLocationId === props.locationId() && saveSessionToken === locationSessionToken()) {
            toast.error("A graph was updated by another user, and automatic refresh failed. Please refresh the page.");
          }
        }
        return;
      }
      if (error instanceof Error && error.message === "CSRF invalid") {
        toast.error("Security token refresh failed. Please retry Apply; your edits are still local.");
        return;
      }
      if (error instanceof Error && error.message === "Security token rejected") {
        toast.error("Security validation failed. Retrying Apply usually succeeds without losing local edits.");
        return;
      }
      if (error instanceof Error && error.message === "Authentication required") {
        toast.error("Session refresh failed. Please sign in again; your local edits are still on this page.");
        return;
      }
      if (error instanceof Error && error.message === "Insufficient permissions") {
        toast.error("You no longer have permission to edit these graphs.");
        return;
      }
      toast.error("Unable to save dashboard changes.");
    } finally {
      if (saveLocationId === props.locationId() && saveSessionToken === locationSessionToken()) {
        setIsSavingGraphChanges(false);
      }
    }
  };

  const retryAll = () => {
    void props.refetchLocation();
    void props.refetchGraphs();
  };

  const updatedAtLabel = () => {
    const current = props.location();
    if (!current) {
      return "-";
    }
    return new Date(current.updatedAt).toLocaleString();
  };

  const resolveGraphCreateFollowUpFailureMessage = (error: unknown): string => {
    if (error instanceof Error && error.message === "CSRF invalid") {
      return "Graph created, but the custom Y-axis title could not be saved because the security token expired. Reopen the graph and try again.";
    }
    if (error instanceof Error && error.message === "Security token rejected") {
      return "Graph created, but the custom Y-axis title could not be saved because security validation failed. Reopen the graph and try again.";
    }
    if (error instanceof Error && error.message === "Authentication required") {
      return "Graph created, but the custom Y-axis title could not be saved because your session expired. Sign in again and retry from the graph editor.";
    }
    if (error instanceof Error && error.message === "Insufficient permissions") {
      return "Graph created, but the custom Y-axis title could not be saved because you no longer have permission to edit graphs.";
    }
    if (error instanceof Error && error.message === "Graph update conflict") {
      return "Graph created, but the custom Y-axis title could not be saved due to a graph update conflict. Reopen the graph and try again.";
    }
    return "Graph created, but the custom Y-axis title could not be saved. Reopen the graph and try again.";
  };

  return {
    workingGraphs,
    workingSectionLayout,
    editingGraphId,
    editingGraph,
    isCreateGraphModalOpen,
    isLayoutEditorOpen,
    isCreatingGraph,
    isDeletingGraph,
    isSavingGraphChanges,
    hasPendingDashboardChanges,
    pendingDashboardMutationCount,
    hasPendingGraphChanges,
    isGraphMutationBusy,
    graphById,
    orderedSections,
    canCreateGraphs,
    sectionOptions,
    nextSectionId,
    updatedAtLabel,
    retryAll,
    openGraphEditor,
    closeGraphEditor,
    openCreateGraphModal,
    closeCreateGraphModal,
    openLayoutEditor,
    closeLayoutEditor,
    sectionGraphs,
    missingGraphIds,
    applyLocalGraphEdit,
    applyLocalSectionLayoutEdit,
    renameGraphFromModal,
    createGraphFromModal,
    deleteGraphFromModal,
    undoLastDashboardEdit,
    applyGraphChanges
  };
};

