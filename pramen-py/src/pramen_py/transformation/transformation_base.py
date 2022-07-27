import abc
import datetime

from typing import ClassVar, Dict, List, TypeVar

import attrs
import click

from pyspark.sql import DataFrame, SparkSession

from pramen_py import MetastoreReader
from pramen_py.models import TransformationConfig


T_EXTRA_OPTIONS = TypeVar("T_EXTRA_OPTIONS")


@attrs.define(auto_attribs=True, slots=True)
class Transformation(metaclass=abc.ABCMeta):
    """Base transformation class.

    All Transformations should implement it and its abc members.
    """

    cli_options: ClassVar[List[click.Parameter]] = []
    spark: SparkSession = attrs.field()
    config: TransformationConfig = attrs.field()

    @abc.abstractmethod
    async def run(
        self,
        metastore: MetastoreReader,
        info_date: datetime.date,
        options: Dict[str, str],
        **kwargs: T_EXTRA_OPTIONS,
    ) -> DataFrame:
        """Abstract method for executing the transformation.

        :param metastore: instance of the MetastoreReader for convenient
            access of the tables
        :param info_date: information date for which the transformer will be
            run
        :param kwargs: if cls.cli_options contains any click.Options, then
            values from these options will be passed here
        :param options: optional settings of the transformer

        Method docstrings will be used as cli command help string.
        """
