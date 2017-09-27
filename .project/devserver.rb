require_relative "common/common"

def package_deps()
  c = Common.new
  env = c.load_env
  unless c.docker.image_exists?("node:alpine")
    c.error "Pulling nodejs image..."
    c.run_inline %W{docker pull node:alpine}
  end
  cname = "#{env.namespace}-node-modules"
  c.run_inline %W{
    docker run -d --name #{cname}
      -v #{cname}:/w/node_modules -w /w
      node:alpine
      sleep 1d
  }
  at_exit { c.run_inline %W{docker rm -f #{cname}} }
  c.run_inline %W{docker cp package.json #{cname}:/w}
  c.run_inline %W{docker cp src/main/js/webpack.config.js #{cname}:/w}
  c.run_inline %W{docker cp src/main/js/webpack-requires.js #{cname}:/w}
  c.run_inline %W{docker start #{cname}}
  c.run_inline %W{docker exec #{cname} npm install}
  c.run_inline %W{
    docker exec #{cname} ./node_modules/webpack/bin/webpack.js --config webpack.config.js
  }
  c.run_inline %W{docker cp #{cname}:/w/webpack-deps.js src/static/webpack-deps.dev.js}
  c.run_inline %W{
    docker exec #{cname} ./node_modules/webpack/bin/webpack.js --config webpack.config.js -p
  }
  c.run_inline %W{docker cp #{cname}:/w/webpack-deps.js src/static/webpack-deps.prod.js}
end

def start_dev()
  c = Common.new
  env = c.load_env
  c.sf.maybe_start_file_syncing
  unless c.docker.image_exists?("dmohs/clojurescript")
    c.error "Pulling ClojureScript image..."
    c.run_inline %W{docker pull dmohs/clojurescript}
  end
  c.status "Starting figwheel. Wait for prompt before connecting with a browser..."
  docker_run = %W{
    docker run --name #{env.namespace}-figwheel
      --rm -it
      -w /w
      -p 3449:3449
      -v jars:/root/.m2
  }
  docker_run += c.sf.get_volume_mounts
  cmd = "sleep 1; rlwrap lein with-profile +unbundled,+ui figwheel"
  docker_run += %W{dmohs/clojurescript sh -c #{cmd}}
  c.run_inline docker_run
end

Common.register_command({
  :invocation => "start",
  :description => "Starts the development compiler and server.",
  :fn => Proc.new { |*args| start_dev(*args) }
})

Common.register_command({
  :invocation => "package-deps",
  :description => "Installs dependencies and packages them using webpack.",
  :fn => Proc.new { |*args| package_deps(*args) }
})
