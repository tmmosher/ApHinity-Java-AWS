type GraphLoadingPlaceholderProps = {
  graphName?: string;
  height?: string;
};

export const GraphLoadingPlaceholder = (props: GraphLoadingPlaceholderProps) => (
  <div
    role="status"
    aria-busy="true"
    aria-label={props.graphName ? `Graph loading: ${props.graphName}` : "Graph loading"}
    data-graph-loading-placeholder=""
    class="graph-loading-shine h-full w-full rounded-lg border border-gray-300"
    style={props.height ? {height: props.height} : undefined}
  />
);

export default GraphLoadingPlaceholder;
