import { createContext, useContext } from "solid-js";
import type { ParentProps } from "solid-js";

// "https://aphinityms.com"
// "http://localhost:8080"

const API_HOST = import.meta.env.VITE_API_HOST as string;

const ApiHostContext = createContext<string>(API_HOST);

export const ApiHostProvider = (props: ParentProps) => (
  <ApiHostContext.Provider value={API_HOST}>
    {props.children}
  </ApiHostContext.Provider>
);

export const useApiHost = () => useContext(ApiHostContext);