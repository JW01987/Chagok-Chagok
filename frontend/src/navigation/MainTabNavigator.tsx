import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import DashboardScreen from '../screens/dashboard/DashboardScreen';
import PortfolioListScreen from '../screens/portfolio/PortfolioListScreen';
import SimulationScreen from '../screens/simulation/SimulationScreen';
import SettingsScreen from '../screens/settings/SettingsScreen';

export type MainTabParamList = {
  Dashboard: undefined;
  Portfolio: undefined;
  Simulation: undefined;
  Settings: undefined;
};

const Tab = createBottomTabNavigator<MainTabParamList>();

export default function MainTabNavigator() {
  return (
    <Tab.Navigator screenOptions={{ headerShown: false }}>
      <Tab.Screen name="Dashboard" component={DashboardScreen} />
      <Tab.Screen name="Portfolio" component={PortfolioListScreen} />
      <Tab.Screen name="Simulation" component={SimulationScreen} />
      <Tab.Screen name="Settings" component={SettingsScreen} />
    </Tab.Navigator>
  );
}
