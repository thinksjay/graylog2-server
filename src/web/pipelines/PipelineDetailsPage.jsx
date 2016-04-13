import React from 'react';
import Reflux from 'reflux';
import { Row, Col, Button } from 'react-bootstrap';
import { LinkContainer } from 'react-router-bootstrap';

import { PageHeader, Spinner } from 'components/common';
import Pipeline from './Pipeline';
import NewPipeline from './NewPipeline';

import SourceGenerator from 'logic/SourceGenerator';
import ObjectUtils from 'util/ObjectUtils';
import PipelinesActions from 'pipelines/PipelinesActions';
import PipelinesStore from 'pipelines/PipelinesStore';
import RulesStore from 'rules/RulesStore';

function filterPipeline(state) {
  return state.pipelines ? state.pipelines.filter(p => p.id === this.props.params.pipelineId)[0] : undefined;
}

const PipelineDetailsPage = React.createClass({
  propTypes: {
    params: React.PropTypes.object.isRequired,
    history: React.PropTypes.object.isRequired,
  },

  mixins: [Reflux.connectFilter(PipelinesStore, 'pipeline', filterPipeline)],

  componentDidMount() {
    if (!this._isNewPipeline(this.props.params.pipelineId)) {
      PipelinesActions.get(this.props.params.pipelineId);
    }
    RulesStore.list();
  },

  componentWillReceiveProps(nextProps) {
    if (!this._isNewPipeline(nextProps.params.pipelineId)) {
      PipelinesActions.get(nextProps.params.pipelineId);
    }
  },

  _onStagesChange(newStages, callback) {
    const newPipeline = ObjectUtils.clone(this.state.pipeline);
    newPipeline.stages = newStages;
    const pipelineSource = SourceGenerator.generatePipeline(newPipeline);
    newPipeline.source = pipelineSource;
    PipelinesActions.update(newPipeline);
    if (typeof callback === 'function') {
      callback();
    }
  },

  _savePipeline(pipeline, callback) {
    const requestPipeline = ObjectUtils.clone(pipeline);
    requestPipeline.source = SourceGenerator.generatePipeline(pipeline);
    let promise;
    if (requestPipeline.id) {
      promise = PipelinesActions.update(requestPipeline);
    } else {
      promise = PipelinesActions.save(requestPipeline);
    }

    promise.then(p => callback(p));
  },

  _isNewPipeline(pipelineId) {
    return pipelineId === 'new';
  },

  _isLoading() {
    return !this._isNewPipeline(this.props.params.pipelineId) && !this.state.pipeline;
  },

  render() {
    if (this._isLoading()) {
      return <Spinner />;
    }

    let title;
    if (this._isNewPipeline(this.props.params.pipelineId)) {
      title = 'New pipeline';
    } else {
      title = <span>Pipeline <em>{this.state.pipeline.title}</em></span>;
    }

    let content;
    if (this._isNewPipeline(this.props.params.pipelineId)) {
      content = <NewPipeline onChange={this._savePipeline} history={this.props.history} />;
    } else {
      content = <Pipeline pipeline={this.state.pipeline} rules={this.state.rules} onStagesChange={this._onStagesChange} onPipelineChange={this._savePipeline} />;
    }

    return (
      <div>
        <PageHeader title={title} titleSize={8} buttonSize={4}>
          <span>
            Pipelines let you transform and process messages coming from streams. Pipelines consist of stages where{' '}
            rules are evaluated and applied. Messages can go through one or more stages.
          </span>
          <span>
            After each stage is completed, you can decide if messages matching all or one of the rules continue to the next stage.
          </span>

          <span>
            <LinkContainer to={'/system/pipelines/overview'}>
              <Button bsStyle="info">Manage pipelines</Button>
            </LinkContainer>
            {' '}
            <LinkContainer to={'/system/pipelines/rules'}>
              <Button bsStyle="info">Manage rules</Button>
            </LinkContainer>
          </span>
        </PageHeader>

        <Row className="content">
          <Col md={12}>
            {content}
          </Col>
        </Row>
      </div>
    );
  },
});

export default PipelineDetailsPage;
