type GraphLoadingPlaceholderProps = {
  graphName?: string;
};

export const GraphLoadingPlaceholder = (props: GraphLoadingPlaceholderProps) => (
  <div
    role="status"
    aria-busy="true"
    aria-label={props.graphName ? `Graph loading: ${props.graphName}` : "Graph loading"}
    data-graph-loading-placeholder=""
    class="graph-loading-shine min-h-[320px] h-full w-full rounded-lg border border-gray-300"
  />
);

export default GraphLoadingPlaceholder;
