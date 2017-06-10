require_relative "common/common"

def build_docs_folder()
  c = Common.new
  env = c.load_env
  cname = "#{env.namespace}-docs-build"
  c.run_inline %W{
    docker create --name #{cname}
      -w /w
      -v jars:/root/.m2
      dmohs/clojurescript
      lein with-profile +docs cljsbuild once
  }
  at_exit { c.run_inline %W{docker rm -f #{cname}} }
  env.source_file_paths.each do |src_path|
    c.pipe(
      %W{tar -c #{src_path}},
      %W{docker cp - #{cname}:/w}
    )
  end
  c.run_inline %W{docker start -a #{cname}}
  c.run_inline %W{rm -rf docs}
  c.run_inline %W{mkdir -p docs/examples/target}
  c.run_inline %W{docker cp #{cname}:/w/resources/public/target/compiled.js docs/examples/target}
  c.sf.foreach_static_file do |path, entry|
    c.run_inline %W{cp -R #{path}/#{entry} docs/examples/#{entry}}
  end
end

def test_docs()
  require "webrick"
  WEBrick::HTTPServer.new(:Port => 8000, :DocumentRoot => "docs/examples").start
end

Common.register_command({
  :invocation => "build-docs",
  :description => "(Re-)Builds the docs/ folder.",
  :fn => Proc.new { |*args| build_docs_folder(*args) }
})

Common.register_command({
  :invocation => "test-docs",
  :description => "Runs an HTTP server against the docs/examples folder.",
  :fn => Proc.new { |*args| test_docs(*args) }
})
