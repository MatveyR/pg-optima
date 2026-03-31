import { createBrowserRouter } from "react-router";
import { Login } from "./pages/Login";
import { Layout } from "./components/Layout";
import { Connections } from "./pages/Connections";
import { SQLEditor } from "./pages/SQLEditor";
import { History } from "./pages/History";
import { Settings } from "./pages/Settings";

export const router = createBrowserRouter([
  {
    path: "/login",
    Component: Login,
  },
  {
    path: "/",
    Component: Layout,
    children: [
      { index: true, Component: SQLEditor },
      { path: "connections", Component: Connections },
      { path: "history", Component: History },
      { path: "settings", Component: Settings },
    ],
  },
]);
