import React, { Component } from "react";
import {
  Router,
  Link,
  Redirect,
  Route,
  Switch,
  withRouter
} from "react-router-dom";
import {
  ApikeyPage,
  ConfigExplorerPage,
  ConfigurationsPage,
  ExperimentsExplorerPage,
  ExperimentsPage,
  FeaturesExplorerPage,
  FeaturesPage,
  GlobalScriptsPage,
  HomePage,
  LoggersPage,
  LoginPage,
  NotFoundPage,
  UserPage,
  WebHooksPage
} from "./pages";
import { popover } from "./inputs/popover";
import queryString from "query-string";
import isEmpty from "lodash/isEmpty";
import "../styles/main.scss";
import { MultiSearch } from "./inputs";
import { DynamicTitle } from "./components/DynamicTitle";
import { IzanamiEvents } from "./services/events";
import * as IzanamiServices from "./services";
import Cookies from "js-cookie";
const pictos = {
  configurations: "fas fa-wrench",
  features: "fas fa-toggle-on",
  experiments: "fas fa-flask",
  scripts: "far fa-file-alt",
  webhooks: "fas fa-plug"
};

export class LoggedApp extends Component {
  decorate = (Component, props) => {
    const newProps = { ...props };
    const query =
      queryString.parse((props.location || { search: "" }).search) || {};
    newProps.location.query = query;
    newProps.params = newProps.match.params || {};
    return (
      <Component
        setTitle={t => DynamicTitle.setContent(t)}
        getTitle={() => DynamicTitle.getContent()}
        user={this.props.user}
        userManagementMode={this.props.userManagementMode}
        confirmationDialog={this.props.confirmationDialog}
        setSidebarContent={c => DynamicSidebar.setContent(c)}
        {...newProps}
      />
    );
  };

  componentDidMount() {
    IzanamiEvents.start();
    this.props.history.listen(() => {
      $("#sidebar").collapse("hide");
      $("#navbar").collapse("hide");
    });
  }

  componentWillUnmount() {
    IzanamiEvents.stop();
  }

  searchServicesOptions = ({ query, filters }) => {
    return IzanamiServices.search({query, filters})
      .then(results => {
        return results.map(v => ({
          label: `${v.id}`,
          type: v.type,
          value: v.id
        }));
      });
  };

  lineRenderer = option => {
    return (
      <label style={{ cursor: "pointer" }}>
        <i className={pictos[option.type]} /> {option.label}
      </label>
    );
  };

  gotoService = e => {
    window.location.href = `/${e.type}/edit/${e.value}`;
  };

  onChangemeClosed = () => {
    Cookies.remove("notifyuser");
  };

  render() {
    const pathname = window.location.pathname;
    const className = part => (part === pathname ? "active" : "inactive");

    const userManagementEnabled =
      (this.props.userManagementMode !== "None") &&
      this.props.user &&
      this.props.user.admin;

    const apikeyManagementEnabled =
      this.props.enabledApikeyManagement &&
      this.props.user &&
      this.props.user.admin;

    const selected = (this.props.params || {}).lineId;

    const changeme = Cookies.get("notifyuser") || this.props.user.changeme;

    return (
      <div className="container-fluid">
        <nav className="navbar navbar-inverse navbar-fixed-top">
          <div className="navbar-header col-md-2">
            <button
              type="button"
              className="navbar-toggle collapsed menu"
              data-toggle="collapse"
              data-target="#sidebar"
              aria-expanded="false"
              aria-controls="sidebar"
            >
              <span className="sr-only">Toggle sidebar</span>
              <span>Menu</span>
            </button>
            <a href="/" className="navbar-brand" style={{ display: "flex" }}>
              イザナミ&nbsp; Izanami
            </a>
          </div>

          <div className="container-fluid">
            <ul key="admin-menu" className="nav navbar-nav navbar-right">
              <li className="dropdown userManagement">
                <a
                  href="#"
                  className="dropdown-toggle"
                  data-toggle="dropdown"
                  role="button"
                  aria-haspopup="true"
                  aria-expanded="false"
                >
                  <i className="fas fa-cog" aria-hidden="true" />
                </a>
                <ul className="dropdown-menu">
                  {userManagementEnabled &&
                    <li key="li-users">
                      <Link
                        to="/users"
                        className=""
                        style={{ cursor: "pointer" }}
                      >
                        Users management
                      </Link>
                    </li>}
                  {apikeyManagementEnabled &&
                    <li key="li-apikeys">
                      <Link
                        to="/apikeys"
                        className=""
                        style={{ cursor: "pointer" }}
                      >
                        Api Keys management
                      </Link>
                    </li>
                  }
                  <li>
                    <a href={this.props.logout} className="link-logout">
                      {this.props.user ? this.props.user.email : ""}&nbsp;
                      <span className="glyphicon glyphicon-off" />
                    </a>
                  </li>
                </ul>
              </li>
            </ul>
            {changeme && (
              <a
                data-toggle="popover"
                data-trigger="focus"
                tabIndex="0"
                role="button"
                style={{
                  paddingLeft: 0,
                  paddingRight: 0,
                  marginLeft: 0,
                  marginRight: 0,
                  width: 0
                }}
                {...popover({
                  options: {
                    title:
                      '<span><strong>Create a user</strong></span><button type="button" class="close cancel pull-right" >&times;</button>',
                    html: "true",
                    content:
                      '<p>You\'re using a temporary user, please create a dedicated one here</p><a class="btn btn-primary pull-right click" href="/users/add">Create user</a>',
                    container: "body"
                  },
                  state: "show",
                  onClick: this.onChangemeClosed,
                  onClose: this.onChangemeClosed
                })}
              />
            )}
            <form className="navbar-form navbar-left">
              {selected && (
                <div
                  className="form-group"
                  style={{ marginRight: 10, display: "inline" }}
                >
                  <span
                    title="Current line"
                    className="label label-success"
                    style={{ fontSize: 20, cursor: "pointer" }}
                  >
                    {selected}
                  </span>
                </div>
              )}
              <div className="form-group" style={{ marginRight: 10 }}>
                <MultiSearch
                  filters={[
                    { name: "features", label: "Features", active: true },
                    { name: "configs", label: "Configurations", active: true },
                    { name: "experiments", label: "Experiments", active: true },
                    { name: "scripts", label: "Scripts", active: true }
                  ]}
                  query={this.searchServicesOptions}
                  lineRenderer={this.lineRenderer}
                  onElementSelected={this.gotoService}
                />
              </div>
            </form>
          </div>
        </nav>

        <div className="container-fluid">
          <div className="row">
            <div className="analytics-viewer-bottom-container">
              <div className="col-sm-2 sidebar" id="sidebar">
                <div className="sidebar-container">
                  <div className="sidebar-content">
                    <ul className="nav nav-sidebar">
                      <li className={className("/")}>
                        <Link to="/">
                          <h3 style={{ marginTop: 0, marginLeft: -25 }}>
                            <i className="fas fa-tachometer-alt" /> Home
                          </h3>
                        </Link>
                      </li>
                      <li className={className("/features")}>
                        <Link to="/features" style={{ cursor: "pointer" }}>
                          <i className="fas fa-toggle-on" />
                          Features
                        </Link>
                      </li>
                      <li className={className("/configurations")}>
                        <Link
                          to="/configurations"
                          className=""
                          style={{ cursor: "pointer" }}
                        >
                          <i className="fas fa-wrench" />
                          Configurations
                        </Link>
                      </li>
                      <li className={className("/experiments")}>
                        <Link
                          to="/experiments"
                          className=""
                          style={{ cursor: "pointer" }}
                        >
                          <i className="fas fa-flask" />
                          Experiments
                        </Link>
                      </li>
                      <li className={className("/scripts")}>
                        <Link
                          to="/scripts"
                          className=""
                          style={{ cursor: "pointer" }}
                        >
                          <i className="far fa-file-alt" />
                          Global Scripts
                        </Link>
                      </li>
                      <li className={className("/webhooks")}>
                        <Link
                          to="/webhooks"
                          className=""
                          style={{ cursor: "pointer" }}
                        >
                          <i className="fas fa-plug" />
                          WebHooks
                        </Link>
                      </li>
                    </ul>
                    <ul className="nav nav-sidebar">
                      <li className={className("/")}>
                        <h3 style={{ marginTop: 0 }}>
                          <i className="fas fa-tachometer-alt" /> Explore
                        </h3>
                      </li>
                      <li className={className("/explorer/features")}>
                        <Link
                          to="/explorer/features"
                          className=""
                          style={{ cursor: "pointer" }}
                        >
                          Features Explorer
                        </Link>
                      </li>
                      <li className={className("/explorer/configs")}>
                        <Link
                          to="/explorer/configs"
                          className=""
                          style={{ cursor: "pointer" }}
                        >
                          Configurations Explorer
                        </Link>
                      </li>
                      <li className={className("/explorer/experiments")}>
                        <Link
                          to="/explorer/experiments"
                          className=""
                          style={{ cursor: "pointer" }}
                        >
                          Experiments Explorer
                        </Link>
                      </li>
                    </ul>
                  </div>
                </div>
              </div>
              <div className="col-sm-10 col-sm-offset-2 main">
                <div className="row">
                  <div className="izanami-container">
                    <DynamicTitle />
                    <div className="row">
                      <Switch>
                        <Route
                          exact
                          path="/"
                          component={props => this.decorate(HomePage, props)}
                        />

                        <Route
                          path="/features/:taction/:titem"
                          component={props =>
                            this.decorate(FeaturesPage, props)
                          }
                        />
                        <Route
                          path="/features/:taction"
                          component={props =>
                            this.decorate(FeaturesPage, props)
                          }
                        />
                        <Route
                          path="/features"
                          component={props =>
                            this.decorate(FeaturesPage, props)
                          }
                        />

                        <Route
                          path="/configurations/:taction/:titem"
                          component={props =>
                            this.decorate(ConfigurationsPage, props)
                          }
                        />
                        <Route
                          path="/configurations/:taction"
                          component={props =>
                            this.decorate(ConfigurationsPage, props)
                          }
                        />
                        <Route
                          path="/configurations"
                          component={props =>
                            this.decorate(ConfigurationsPage, props)
                          }
                        />

                        <Route
                          path="/webhooks/:taction/:titem"
                          component={props =>
                            this.decorate(WebHooksPage, props)
                          }
                        />
                        <Route
                          path="/webhooks/:taction"
                          component={props =>
                            this.decorate(WebHooksPage, props)
                          }
                        />
                        <Route
                          path="/webhooks"
                          component={props =>
                            this.decorate(WebHooksPage, props)
                          }
                        />

                        <Route
                          path="/scripts/:taction/:titem"
                          component={props =>
                            this.decorate(GlobalScriptsPage, props)
                          }
                        />
                        <Route
                          path="/scripts/:taction"
                          component={props =>
                            this.decorate(GlobalScriptsPage, props)
                          }
                        />
                        <Route
                          path="/scripts"
                          component={props =>
                            this.decorate(GlobalScriptsPage, props)
                          }
                        />

                        <Route
                          path="/experiments/:taction/:titem"
                          component={props =>
                            this.decorate(ExperimentsPage, props)
                          }
                        />
                        <Route
                          path="/experiments/:taction"
                          component={props =>
                            this.decorate(ExperimentsPage, props)
                          }
                        />
                        <Route
                          path="/experiments"
                          component={props =>
                            this.decorate(ExperimentsPage, props)
                          }
                        />
                        <Route
                          path="/loggers"
                          component={props => this.decorate(LoggersPage, props)}
                        />

                        {userManagementEnabled && (
                          <Route
                            path="/users/:taction/:titem"
                            component={props => this.decorate(UserPage, props)}
                          />
                        )}
                        {userManagementEnabled && (
                          <Route
                            path="/users/:taction"
                            component={props => this.decorate(UserPage, props)}
                          />
                        )}
                        {userManagementEnabled && (
                          <Route
                            path="/users"
                            component={props => this.decorate(UserPage, props)}
                          />
                        )}

                        {apikeyManagementEnabled && (
                          <Route
                            path="/apikeys/:taction/:titem"
                            component={props =>
                              this.decorate(ApikeyPage, props)
                            }
                          />
                        )}
                        {apikeyManagementEnabled && (
                          <Route
                            path="/apikeys/:taction"
                            component={props =>
                              this.decorate(ApikeyPage, props)
                            }
                          />
                        )}
                        {apikeyManagementEnabled && (
                          <Route
                            path="/apikeys"
                            component={props =>
                              this.decorate(ApikeyPage, props)
                            }
                          />
                        )}

                        <Route
                          path="/explorer/configs"
                          component={props =>
                            this.decorate(ConfigExplorerPage, props)
                          }
                        />
                        <Route
                          path="/explorer/features"
                          component={props =>
                            this.decorate(FeaturesExplorerPage, props)
                          }
                        />
                        <Route
                          path="/explorer/experiments"
                          component={props =>
                            this.decorate(ExperimentsExplorerPage, props)
                          }
                        />
                        <Route
                          component={props =>
                            this.decorate(NotFoundPage, props)
                          }
                        />
                      </Switch>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    );
  }
}

export class IzanamiApp extends Component {
  render() {
    return (
      <Switch>
        <Route key="route-login" path="/login" component={LoginPage} />,
        <PrivateRoute
          key="private-route"
          path="/"
          component={LoggedApp}
          {...this.props}
        />
      </Switch>
    );
  }
}

const PrivateRoute = ({ component: Component, ...rest }) => {
  return (
    <Route
      {...rest}
      render={props => {
        //User is passed from the LoginPage or send by the app in the page.
        const user =
          rest.user && !isEmpty(rest.user)
            ? rest.user
            : props.location.user || {};
        return user.email ? (
          <Component {...rest} {...props} user={user} />
        ) : (
          <Redirect
            to={{
              pathname: `${window.__contextPath}/login`,
              state: { from: props.location }
            }}
          />
        );
      }}
    />
  );
};

const IzanamiAppRouter = withRouter(IzanamiApp);

export function buildRoutedApp(browserHistory) {
  return class RoutedIzanamiApp extends Component {
    render() {
      return (
        <Router history={browserHistory}>
          <IzanamiAppRouter {...this.props} />
        </Router>
      );
    }
  };
}
