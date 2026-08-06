import { registerRootComponent } from 'expo';
import React from 'react';
import { SafeAreaView, StyleSheet, Text } from 'react-native';

function App() {
  return (
    <SafeAreaView style={styles.container}>
      <Text>AERIS Native CI</Text>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
});

registerRootComponent(App);
