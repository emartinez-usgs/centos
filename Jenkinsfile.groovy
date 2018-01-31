#!/usr/bin/env groovy

node {
  def CONTAINER_BASE = null
  def FROM_IMAGE = null
  def IMAGE_NAME = null
  def IMAGE_VERSION = null
  def SCM_VARS = null

  CONTAINER_BASE = "${GITLAB_INNERSOURCE_REGISTRY}/devops/containers"
  FROM_IMAGE = "${CONTAINER_BASE}/library/centos"
  IMAGE_NAME = "${CONTAINER_BASE}/centos"

  try {
    stage('Initialize') {
      cleanWs()

      SCM_VARS = checkout scm

      if (params.GIT_BRANCH != '') {
        sh "git checkout --detach ${params.GIT_BRANCH}"

        SCM_VARS.GIT_BRANCH = params.GIT_BRANCH
        SCM_VARS.GIT_COMMIT = sh(
          returnStdout: true,
          script: "git rev-parse HEAD"
        )
      }

      if (SCM_VARS.GIT_BRANCH == 'origin/master') {
        // git master --> docker latest
        IMAGE_VERSION = 'latest'
      } else if (SCM_VARS.GIT_BRANCH.substring(0, 7) == 'origin/') {
        // git origin/BRANCH --> docker BRANCH
        IMAGE_VERSION = SCM_VARS.GIT_BRANCH.substring(7).replace(' ', '_')
      } else {
        // git TAG --> docker TAG
        IMAGE_VERSION = SCM_VARS.GIT_BRANCH.replace(' ', '_')
      }

      SCM_VARS.each { key, value ->
        echo "${key} = ${value}"
      }

      echo "IMAGE_VERSION = ${IMAGE_VERSION}"
    }

    // stage('Build') {
    //   ansiColor('xterm') {
    //     sh """
    //       docker build \
    //         --build-arg FROM_IMAGE=${FROM_IMAGE}:${params.IMAGE_VERSION}
    //         -t ${IMAGE_NAME}:${params.IMAGE_VERSION} .
    //     """
    //   }
    // }

    // stage('Publish') {
    //   withCredentials([usernamePassword(
    //       credentialsId: 'innersource-hazdev-cicd',
    //       passwordVariable: 'REGISTRY_PASSWORD',
    //       usernameVariable: 'REGISTRY_USERNAME')
    //   ]) {
    //     ansiColor('xterm') {
    //       sh """
    //         docker login ${GITLAB_INNERSOURCE_REGISTRY} \
    //           -u ${REGISTRY_USERNAME} \
    //           -p ${REGISTRY_PASSWORD}

    //         docker push ${IMAGE_NAME}:${params.IMAGE_VERSION}
    //       """
    //     }

    //     if (params.TAG_LATEST && params.IMAGE_VERSION != 'latest') {
    //       ansiColor('xterm') {
    //         sh """
    //           docker tag \
    //             ${IMAGE_NAME}:${params.IMAGE_VERSION} \
    //             ${IMAGE_NAME}:latest

    //           docker login ${GITLAB_INNERSOURCE_REGISTRY} \
    //             -u ${REGISTRY_USERNAME} \
    //             -p ${REGISTRY_PASSWORD}

    //           docker push ${IMAGE_NAME}:${latest}
    //         """
    //       }
    //     }
    //   }
    // }
  } catch (err) {
    def message
    def printWriter
    def stringWriter

    try {
      stringWriter = new StringWriter()
      printWriter = new PrintWriter(stringWriter)
      err.printStackTrace(printWriter)

      message = stringWriter.toString()

      mail([
        to: 'gs-haz_dev_team_group@usgs.gov',
        from: 'noreply@jenkins',
        subject: "Jenkins Pipeline Failed: ${env.BUILD_TAG}",
        body: "Details: ${message}"
      ])
    } catch (inner) {
      echo "${inner}"
    }

    currentBuild.result = 'FAILURE'
    throw err
  }
}