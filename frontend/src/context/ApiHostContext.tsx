import { createContext, useContext } from "solid-js";
import type { ParentProps } from "solid-js";

const API_HOST = "http://localhost:8080";

const ApiHostContext = createContext<string>(API_HOST);

export const ApiHostProvider = (props: ParentProps) => (
  <ApiHostContext.Provider value={API_HOST}>
    {props.children}
  </ApiHostContext.Provider>
);

export const useApiHost = () => useContext(ApiHostContext);

export { API_HOST };
