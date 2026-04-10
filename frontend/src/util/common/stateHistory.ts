export type StateHistoryApplyResult<T> = {
  nextState: T;
  nextUndoStack: T[];
  changed: boolean;
};

export type StateHistoryUndoResult<T> = {
  nextState: T;
  nextUndoStack: T[];
  undone: boolean;
};

export const applyStateSnapshot = <T>(
  currentState: T,
  undoStack: T[],
  nextState: T,
  cloneState: (state: T) => T,
  areEqual: (left: T, right: T) => boolean = Object.is
): StateHistoryApplyResult<T> => {
  if (areEqual(currentState, nextState)) {
    return {
      nextState: currentState,
      nextUndoStack: undoStack,
      changed: false
    };
  }

  return {
    nextState,
    nextUndoStack: [...undoStack, cloneState(currentState)],
    changed: true
  };
};

export const undoStateSnapshot = <T>(
  currentState: T,
  undoStack: T[],
  cloneState: (state: T) => T
): StateHistoryUndoResult<T> => {
  if (undoStack.length === 0) {
    return {
      nextState: currentState,
      nextUndoStack: undoStack,
      undone: false
    };
  }

  return {
    nextState: cloneState(undoStack[undoStack.length - 1]),
    nextUndoStack: undoStack.slice(0, -1),
    undone: true
  };
};
