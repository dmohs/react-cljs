const path = require('path');

module.exports = {
  entry: "./webpack-requires.js",
  output: {
    path: path.resolve(__dirname),
    filename: 'webpack-deps.js'
  }
};
