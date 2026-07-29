import {createContext, useContext, type Accessor, type ParentProps} from "solid-js";

type DashboardTimeRangeContextValue = {
  monthRange: Accessor<number>;
};

const DashboardTimeRangeContext = createContext<DashboardTimeRangeContextValue>();

type DashboardTimeRangeProviderProps = ParentProps<{
  monthRange: Accessor<number>;
}>;

export const DashboardTimeRangeProvider = (props: DashboardTimeRangeProviderProps) => (
  <DashboardTimeRangeContext.Provider value={{monthRange: props.monthRange}}>
    {props.children}
  </DashboardTimeRangeContext.Provider>
);

export const useDashboardTimeRange = (): DashboardTimeRangeContextValue => {
  const context = useContext(DashboardTimeRangeContext);
  if (!context) {
    throw new Error("Dashboard time range is unavailable.");
  }
  return context;
};
