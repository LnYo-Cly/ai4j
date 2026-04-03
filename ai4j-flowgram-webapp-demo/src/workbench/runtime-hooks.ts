import { useEffect, useState } from 'react';

import {
  WorkflowRuntimeService,
  WorkflowRuntimeSnapshot,
} from '../plugins/runtime-plugin/runtime-service';

export const useRuntimeSnapshot = (
  runtimeService: WorkflowRuntimeService
): WorkflowRuntimeSnapshot => {
  const [snapshot, setSnapshot] = useState<WorkflowRuntimeSnapshot>(() =>
    runtimeService.getSnapshot()
  );

  useEffect(() => {
    setSnapshot(runtimeService.getSnapshot());
    const disposable = runtimeService.onSnapshotChanged((nextSnapshot) => {
      setSnapshot(nextSnapshot);
    });
    return () => disposable.dispose();
  }, [runtimeService]);

  return snapshot;
};
